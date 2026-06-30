package com.pillmate.doselog.application;

import com.pillmate.activity.application.ActivityFeedAppender;
import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.doselog.application.dto.BulkCheckDoseRequest;
import com.pillmate.doselog.application.dto.DoseLogResponse;
import com.pillmate.doselog.domain.event.DoseCheckCanceled;
import com.pillmate.doselog.domain.model.DoseLog;
import com.pillmate.doselog.domain.model.DoseStatus;
import com.pillmate.doselog.domain.repository.DoseLogRepository;
import com.pillmate.schedule.domain.model.Schedule;
import com.pillmate.schedule.domain.model.TimeOfDay;
import com.pillmate.schedule.domain.repository.ScheduleRepository;
import com.pillmate.user.domain.model.User;
import com.pillmate.user.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@DisplayName("BulkCheckDoseUseCase — 슬롯 단위 일괄 체크 (ActivityFeed/이벤트 1회)")
@ExtendWith(MockitoExtension.class)
class BulkCheckDoseUseCaseTest {

    private static final Long PATIENT_ID = 1L;
    private static final Long SCHEDULE_ID = 10L;
    private static final Instant SCHEDULED = Instant.parse("2026-06-30T03:00:00Z");

    @Mock DoseLogRepository doseLogRepository;
    @Mock ScheduleRepository scheduleRepository;
    @Mock UserRepository userRepository;
    @Mock ActivityFeedAppender activityFeedAppender;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks BulkCheckDoseUseCase sut;

    @BeforeEach
    void setUp() {
        lenient().when(doseLogRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        Schedule schedule = org.mockito.Mockito.mock(Schedule.class);
        lenient().when(schedule.getTimeOfDay()).thenReturn(TimeOfDay.NOON);
        lenient().when(scheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(schedule));
        User user = org.mockito.Mockito.mock(User.class);
        lenient().when(user.getName()).thenReturn("테스트유저");
        lenient().when(userRepository.findById(PATIENT_ID)).thenReturn(Optional.of(user));
    }

    private DoseLog doseLog(Long id) {
        DoseLog dl = DoseLog.of(SCHEDULE_ID, PATIENT_ID, SCHEDULED);
        ReflectionTestUtils.setField(dl, "id", id);
        return dl;
    }

    private BulkCheckDoseRequest req(String action, Long... ids) {
        return new BulkCheckDoseRequest(List.of(ids), action, null);
    }

    @Test
    @DisplayName("TAKE 3개 → 모두 TAKEN, appendTaken 1회만 (슬롯 단위)")
    void bulkCheck_takeAll_singleActivityFeed() {
        List<DoseLog> logs = List.of(doseLog(101L), doseLog(102L), doseLog(103L));
        given(doseLogRepository.findAllByIdIn(List.of(101L, 102L, 103L))).willReturn(logs);

        List<DoseLogResponse> result = sut.bulkCheck(req("TAKE", 101L, 102L, 103L), PATIENT_ID);

        assertThatStatusAll(result, DoseStatus.TAKEN);
        then(activityFeedAppender).should(times(1)).appendTaken(PATIENT_ID, TimeOfDay.NOON, "테스트유저");
        then(eventPublisher).should(never()).publishEvent(any());
    }

    @Test
    @DisplayName("CANCEL 3개(모두 TAKEN) → DoseCheckCanceled 1회 + appendCanceled 1회")
    void bulkCheck_cancelAll_singlePublishAndAppend() {
        List<DoseLog> logs = List.of(doseLog(101L), doseLog(102L), doseLog(103L));
        logs.forEach(dl -> dl.take(PATIENT_ID));
        given(doseLogRepository.findAllByIdIn(List.of(101L, 102L, 103L))).willReturn(logs);

        sut.bulkCheck(req("CANCEL", 101L, 102L, 103L), PATIENT_ID);

        then(eventPublisher).should(times(1)).publishEvent(any(DoseCheckCanceled.class));
        then(activityFeedAppender).should(times(1)).appendCanceled(PATIENT_ID, TimeOfDay.NOON, "테스트유저");
    }

    @Test
    @DisplayName("TAKE — 일부 이미 TAKEN, 일부 PENDING → 전이 있으므로 appendTaken 1회")
    void bulkCheck_mixedAlreadyDone_appendsOnTransition() {
        DoseLog already = doseLog(101L);
        already.take(PATIENT_ID);
        List<DoseLog> logs = List.of(already, doseLog(102L));
        given(doseLogRepository.findAllByIdIn(List.of(101L, 102L))).willReturn(logs);

        sut.bulkCheck(req("TAKE", 101L, 102L), PATIENT_ID);

        then(activityFeedAppender).should(times(1)).appendTaken(PATIENT_ID, TimeOfDay.NOON, "테스트유저");
    }

    @Test
    @DisplayName("TAKE — 모두 이미 TAKEN(전이 0) → appendTaken 0회 (멱등)")
    void bulkCheck_allAlreadyTaken_noActivity() {
        List<DoseLog> logs = List.of(doseLog(101L), doseLog(102L));
        logs.forEach(dl -> dl.take(PATIENT_ID));
        given(doseLogRepository.findAllByIdIn(List.of(101L, 102L))).willReturn(logs);

        sut.bulkCheck(req("TAKE", 101L, 102L), PATIENT_ID);

        then(activityFeedAppender).should(never()).appendTaken(anyLong(), any(), any());
    }

    @Test
    @DisplayName("본인 아닌 doseLog 포함 → PATIENT_ACCESS_DENIED, 저장/발화 0")
    void bulkCheck_otherUserDoseLog_accessDenied() {
        DoseLog mine = doseLog(101L);
        DoseLog others = DoseLog.of(SCHEDULE_ID, 99L, SCHEDULED);
        ReflectionTestUtils.setField(others, "id", 102L);
        given(doseLogRepository.findAllByIdIn(List.of(101L, 102L))).willReturn(List.of(mine, others));

        assertThatThrownBy(() -> sut.bulkCheck(req("TAKE", 101L, 102L), PATIENT_ID))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PATIENT_ACCESS_DENIED);
        then(doseLogRepository).should(never()).saveAll(any());
        then(activityFeedAppender).should(never()).appendTaken(anyLong(), any(), any());
    }

    private void assertThatStatusAll(List<DoseLogResponse> result, DoseStatus expected) {
        org.assertj.core.api.Assertions.assertThat(result)
                .allSatisfy(r -> org.assertj.core.api.Assertions.assertThat(r.status()).isEqualTo(expected));
    }
}
