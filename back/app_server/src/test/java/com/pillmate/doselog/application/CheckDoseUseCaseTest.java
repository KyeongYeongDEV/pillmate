package com.pillmate.doselog.application;

import com.pillmate.activity.application.ActivityFeedAppender;
import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.common.security.UserContext;
import com.pillmate.doselog.application.dto.CheckDoseRequest;
import com.pillmate.doselog.application.dto.DoseLogResponse;
import com.pillmate.doselog.domain.event.DoseCheckCanceled;
import com.pillmate.doselog.domain.model.DoseLog;
import com.pillmate.doselog.domain.model.DoseStatus;
import com.pillmate.doselog.domain.repository.DoseLogRepository;
import com.pillmate.schedule.domain.repository.ScheduleRepository;
import com.pillmate.user.domain.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@DisplayName("CheckDoseUseCase — TAKE/SKIP/CANCEL/권한/멱등/취소 활동피드 단위")
@ExtendWith(MockitoExtension.class)
class CheckDoseUseCaseTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-06-12T10:00:00Z");
    private static final Instant SCHEDULED_TODAY = Instant.parse("2026-06-11T23:00:00Z");
    private static final Instant SCHEDULED_YESTERDAY = Instant.parse("2026-06-10T23:00:00Z");
    private static final Instant SCHEDULED_TOMORROW = Instant.parse("2026-06-12T23:00:00Z");

    @Mock DoseLogRepository doseLogRepository;
    @Mock ScheduleRepository scheduleRepository;
    @Mock UserRepository userRepository;
    @Mock ActivityFeedAppender activityFeedAppender;
    @Mock ApplicationEventPublisher eventPublisher;
    @Spy  Clock clock = Clock.fixed(FIXED_NOW, ZoneId.of("Asia/Seoul"));
    @InjectMocks CheckDoseUseCase sut;

    private static final Long PATIENT_ID = 1L;
    private static final Long DOSE_LOG_ID = 5L;
    private static final Long SCHEDULE_ID = 10L;

    @BeforeEach
    void setUp() {
        UserContext.set(PATIENT_ID);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("TAKE 액션 시 DoseLog status TAKEN 전환 + ActivityFeedAppender 호출")
    void check_whenActionTake_marksDoseLogTaken() {
        // given
        DoseLog doseLog = DoseLog.of(SCHEDULE_ID, PATIENT_ID, SCHEDULED_TODAY);
        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));
        given(doseLogRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.empty());

        // when
        DoseLogResponse response = sut.check(new CheckDoseRequest(DOSE_LOG_ID, "TAKE", null), PATIENT_ID);

        // then
        assertThat(response.status()).isEqualTo(DoseStatus.TAKEN);
        assertThat(response.checkedBy()).isEqualTo(PATIENT_ID);
    }

    @Test
    @DisplayName("UserContext 와 patient_id 불일치 시 PATIENT_ACCESS_DENIED (PILL_016) 예외")
    void check_whenAuthorIsNotPatient_throws() {
        // given
        UserContext.set(99L);
        DoseLog doseLog = DoseLog.of(SCHEDULE_ID, PATIENT_ID, SCHEDULED_TODAY);
        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));

        // when / then
        assertThatThrownBy(() -> sut.check(new CheckDoseRequest(DOSE_LOG_ID, "TAKE", null), 99L))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PATIENT_ACCESS_DENIED);
    }

    @Test
    @DisplayName("이미 TAKEN 상태에서 재호출 시 멱등 — status 변경 없음")
    void check_whenAlreadyTaken_isIdempotent() {
        // given
        DoseLog doseLog = DoseLog.of(SCHEDULE_ID, PATIENT_ID, SCHEDULED_TODAY);
        doseLog.take(PATIENT_ID);
        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));
        given(doseLogRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.empty());

        // when
        DoseLogResponse response = sut.check(new CheckDoseRequest(DOSE_LOG_ID, "TAKE", null), PATIENT_ID);

        // then
        assertThat(response.status()).isEqualTo(DoseStatus.TAKEN);
    }

    @Test
    @DisplayName("CANCEL 액션 — TAKEN 에서 PENDING 복귀")
    void check_whenCancel_revertsToPending() {
        // given
        DoseLog doseLog = DoseLog.of(SCHEDULE_ID, PATIENT_ID, SCHEDULED_TODAY);
        doseLog.take(PATIENT_ID);
        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));
        given(doseLogRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        DoseLogResponse response = sut.check(new CheckDoseRequest(DOSE_LOG_ID, "CANCEL", null), PATIENT_ID);

        // then
        assertThat(response.status()).isEqualTo(DoseStatus.PENDING);
        assertThat(response.checkedBy()).isNull();
        assertThat(response.checkedAt()).isNull();
    }

    @Test
    @DisplayName("CANCEL — 그룹 알림 이미 발송됨(groupNotifiedAt 기록) → DoseCheckCanceled 이벤트 발행")
    void check_whenCancelAfterGroupNotified_publishesCanceledEvent() {
        // given
        DoseLog doseLog = DoseLog.of(SCHEDULE_ID, PATIENT_ID, SCHEDULED_TODAY);
        doseLog.take(PATIENT_ID);
        doseLog.markGroupNotified(FIXED_NOW);
        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));
        given(doseLogRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        sut.check(new CheckDoseRequest(DOSE_LOG_ID, "CANCEL", null), PATIENT_ID);

        // then
        ArgumentCaptor<DoseCheckCanceled> captor = ArgumentCaptor.forClass(DoseCheckCanceled.class);
        then(eventPublisher).should().publishEvent(captor.capture());
        assertThat(captor.getValue().actorUserId()).isEqualTo(PATIENT_ID);
        assertThat(captor.getValue().scheduleId()).isEqualTo(SCHEDULE_ID);
    }

    // T-SESSION-2026-06-29-RETRO Group D: groupNotified 조건 제거 → 알림 발송 여부 무관 publish.
    // T-CANCEL-IDEMPOTENT-GUARD: 단, 실제 TAKEN→PENDING 전이 시에만 (cancel()==true).
    @Test
    @DisplayName("CANCEL(TAKEN) — 실제 취소 전이 시 DoseCheckCanceled 이벤트 발행")
    void cancel_whenTaken_publishesEvent() {
        // given — TAKEN 상태 (groupNotified 기록 없음)
        DoseLog doseLog = DoseLog.of(SCHEDULE_ID, PATIENT_ID, SCHEDULED_TODAY);
        doseLog.take(PATIENT_ID);
        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));
        given(doseLogRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        sut.check(new CheckDoseRequest(DOSE_LOG_ID, "CANCEL", null), PATIENT_ID);

        // then — 발행됨
        ArgumentCaptor<DoseCheckCanceled> captor = ArgumentCaptor.forClass(DoseCheckCanceled.class);
        then(eventPublisher).should().publishEvent(captor.capture());
        assertThat(captor.getValue().actorUserId()).isEqualTo(PATIENT_ID);
        assertThat(captor.getValue().scheduleId()).isEqualTo(SCHEDULE_ID);
    }

    @Test
    @DisplayName("CANCEL(이미 PENDING) — 멱등 가드: 전이 없으면 publish/append 0회 (중복 적재 방지)")
    void cancel_whenAlreadyPending_skipsPublishAndAppend() {
        // given — 이미 PENDING (take 호출 X)
        DoseLog doseLog = DoseLog.of(SCHEDULE_ID, PATIENT_ID, SCHEDULED_TODAY);
        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));
        given(doseLogRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        sut.check(new CheckDoseRequest(DOSE_LOG_ID, "CANCEL", null), PATIENT_ID);

        // then — 전이 없으니 이벤트/활동피드 미적재
        then(eventPublisher).should(never()).publishEvent(any());
        then(activityFeedAppender).should(never()).appendCanceled(any(), any(), any(), any());
    }

    @Test
    @DisplayName("CANCEL — schedule 조회되면 ActivityFeedAppender.appendCanceled 호출 (그룹 피드 적재)")
    void cancel_appendsCanceledActivity() {
        // given
        DoseLog doseLog = DoseLog.of(SCHEDULE_ID, PATIENT_ID, SCHEDULED_TODAY);
        doseLog.take(PATIENT_ID);
        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));
        given(doseLogRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        com.pillmate.schedule.domain.model.Schedule schedule =
                org.mockito.Mockito.mock(com.pillmate.schedule.domain.model.Schedule.class);
        given(schedule.getTimeOfDay()).willReturn(com.pillmate.schedule.domain.model.TimeOfDay.EVENING);
        given(schedule.getCustomTime()).willReturn(java.time.LocalTime.of(19, 0));
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        com.pillmate.user.domain.model.User user =
                org.mockito.Mockito.mock(com.pillmate.user.domain.model.User.class);
        given(user.getName()).willReturn("아버지");
        given(userRepository.findById(PATIENT_ID)).willReturn(Optional.of(user));

        // when
        sut.check(new CheckDoseRequest(DOSE_LOG_ID, "CANCEL", null), PATIENT_ID);

        // then
        then(activityFeedAppender).should()
                .appendCanceled(PATIENT_ID, com.pillmate.schedule.domain.model.TimeOfDay.EVENING, "19:00", "아버지");
    }

    @Test
    @DisplayName("SKIP 액션 시 DoseLog status SKIPPED + skipReason 저장")
    void check_whenSkip_marksSkippedWithReason() {
        // given
        DoseLog doseLog = DoseLog.of(SCHEDULE_ID, PATIENT_ID, SCHEDULED_TODAY);
        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));
        given(doseLogRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        DoseLogResponse response = sut.check(new CheckDoseRequest(DOSE_LOG_ID, "SKIP", "운동 중"), PATIENT_ID);

        // then
        assertThat(response.status()).isEqualTo(DoseStatus.SKIPPED);
        assertThat(response.skipReason()).isEqualTo("운동 중");
    }

    // T-SESSION-2026-06-29-RETRO Group C: verifyEditableToday(날짜 잠금) 제거 — 어제/내일 예정분도
    // TAKE/SKIP/CANCEL 모두 허용(사용자 명시 동의, 까먹은 약 체크). 구 DOSE_LOG_DATE_LOCKED 거부 테스트 폐기.
    @Test
    @DisplayName("어제(KST) 예정분 TAKE — 이제 거부 없이 정상 TAKEN (날짜 잠금 제거)")
    void check_whenScheduledYesterday_nowAllowed() {
        // given
        DoseLog doseLog = DoseLog.of(SCHEDULE_ID, PATIENT_ID, SCHEDULED_YESTERDAY);
        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));
        given(doseLogRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.empty());

        // when
        DoseLogResponse response = sut.check(new CheckDoseRequest(DOSE_LOG_ID, "TAKE", null), PATIENT_ID);

        // then — 거부 없이 TAKEN
        assertThat(response.status()).isEqualTo(DoseStatus.TAKEN);
        then(doseLogRepository).should().save(any());
    }

    @Test
    @DisplayName("내일(KST) 예정분 SKIP — 이제 거부 없이 정상 SKIPPED")
    void check_whenScheduledTomorrow_nowAllowed() {
        // given
        DoseLog doseLog = DoseLog.of(SCHEDULE_ID, PATIENT_ID, SCHEDULED_TOMORROW);
        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));
        given(doseLogRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        DoseLogResponse response = sut.check(new CheckDoseRequest(DOSE_LOG_ID, "SKIP", "외출"), PATIENT_ID);

        // then
        assertThat(response.status()).isEqualTo(DoseStatus.SKIPPED);
    }
}
