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

@DisplayName("CheckDoseUseCase — TAKE/SKIP/CANCEL/권한/멱등/날짜 잠금 단위")
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

    @Test
    @DisplayName("CANCEL — 60초 내(그룹 알림 미발송) → 이벤트 미발행, 조용히 복귀")
    void check_whenCancelBeforeGroupNotified_noEvent() {
        // given
        DoseLog doseLog = DoseLog.of(SCHEDULE_ID, PATIENT_ID, SCHEDULED_TODAY);
        doseLog.take(PATIENT_ID);
        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));
        given(doseLogRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        sut.check(new CheckDoseRequest(DOSE_LOG_ID, "CANCEL", null), PATIENT_ID);

        // then
        then(eventPublisher).should(never()).publishEvent(any(DoseCheckCanceled.class));
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

    @Test
    @DisplayName("어제(KST) dose_log TAKE — DOSE_LOG_DATE_LOCKED 거부, 저장 없음")
    void check_whenScheduledYesterday_takeRejected() {
        // given
        DoseLog doseLog = DoseLog.of(SCHEDULE_ID, PATIENT_ID, SCHEDULED_YESTERDAY);
        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));

        // when / then
        assertThatThrownBy(() -> sut.check(new CheckDoseRequest(DOSE_LOG_ID, "TAKE", null), PATIENT_ID))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DOSE_LOG_DATE_LOCKED);
        then(doseLogRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("내일(KST) dose_log TAKE — DOSE_LOG_DATE_LOCKED 거부")
    void check_whenScheduledTomorrow_takeRejected() {
        // given
        DoseLog doseLog = DoseLog.of(SCHEDULE_ID, PATIENT_ID, SCHEDULED_TOMORROW);
        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));

        // when / then
        assertThatThrownBy(() -> sut.check(new CheckDoseRequest(DOSE_LOG_ID, "TAKE", null), PATIENT_ID))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DOSE_LOG_DATE_LOCKED);
    }

    @Test
    @DisplayName("어제(KST) dose_log CANCEL — TAKE 와 동일하게 거부, 이벤트 미발행")
    void check_whenScheduledYesterday_cancelRejected() {
        // given
        DoseLog doseLog = DoseLog.of(SCHEDULE_ID, PATIENT_ID, SCHEDULED_YESTERDAY);
        doseLog.take(PATIENT_ID);
        doseLog.markGroupNotified(FIXED_NOW.minusSeconds(86_400));
        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));

        // when / then
        assertThatThrownBy(() -> sut.check(new CheckDoseRequest(DOSE_LOG_ID, "CANCEL", null), PATIENT_ID))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DOSE_LOG_DATE_LOCKED);
        then(eventPublisher).should(never()).publishEvent(any());
    }

    @Test
    @DisplayName("어제(KST) dose_log SKIP — 동일 거부")
    void check_whenScheduledYesterday_skipRejected() {
        // given
        DoseLog doseLog = DoseLog.of(SCHEDULE_ID, PATIENT_ID, SCHEDULED_YESTERDAY);
        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));

        // when / then
        assertThatThrownBy(() -> sut.check(new CheckDoseRequest(DOSE_LOG_ID, "SKIP", "외출"), PATIENT_ID))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DOSE_LOG_DATE_LOCKED);
    }
}
