package com.pillmate.schedule.application;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.common.security.CareGroupGuard;
import com.pillmate.common.security.PatientAccessGuard;
import com.pillmate.common.security.UserContext;
import com.pillmate.schedule.application.dto.ScheduleResponse;
import com.pillmate.schedule.application.dto.UpdateScheduleRequest;
import com.pillmate.schedule.application.port.RescheduleDoseLogsPort;
import com.pillmate.schedule.domain.model.Schedule;
import com.pillmate.schedule.domain.model.TimeOfDay;
import com.pillmate.schedule.domain.repository.ScheduleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("UpdateScheduleUseCase — customTime 수정 + 기간종료 lock + PENDING 재예약 위임")
@ExtendWith(MockitoExtension.class)
class UpdateScheduleUseCaseTest {

    @Mock ScheduleRepository scheduleRepository;
    @Mock RescheduleDoseLogsPort rescheduleDoseLogsPort;
    @Mock CareGroupGuard careGroupGuard;
    @Mock PatientAccessGuard patientAccessGuard;

    private static final Long SCHEDULE_ID = 1L;
    private static final LocalDate START = LocalDate.of(2026, 5, 25);
    private static final LocalDate END = LocalDate.of(2026, 6, 1);

    // KST 2026-05-28 12:00 (기간 내)
    private static final Clock WITHIN_PERIOD = Clock.fixed(Instant.parse("2026-05-28T03:00:00Z"), ZoneOffset.UTC);
    // KST 2026-06-02 12:00 (기간 종료 후)
    private static final Clock AFTER_PERIOD = Clock.fixed(Instant.parse("2026-06-02T03:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        UserContext.set(2L);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private Schedule schedule(LocalDate endDate) {
        Schedule s = Schedule.of(5L, 2L, 10L, TimeOfDay.MORNING, LocalTime.of(8, 0), START, endDate, 1L);
        ReflectionTestUtils.setField(s, "id", SCHEDULE_ID);
        return s;
    }

    private UpdateScheduleUseCase sutWith(Clock clock) {
        return new UpdateScheduleUseCase(scheduleRepository, rescheduleDoseLogsPort, careGroupGuard, patientAccessGuard, clock);
    }

    @Test
    @DisplayName("기간 내 customTime 수정 → customTime 변경 + 오늘 기준 재예약 port 위임")
    void update_withinPeriod_changesCustomTimeAndDelegatesReschedule() {
        // given
        Schedule schedule = schedule(END);
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(scheduleRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        UpdateScheduleRequest request = new UpdateScheduleRequest(null, LocalTime.of(9, 30), null);

        // when
        ScheduleResponse response = sutWith(WITHIN_PERIOD).update(SCHEDULE_ID, request);

        // then — 스케줄 시각 변경 + 오늘(KST 2026-05-28) 기준 재예약 위임
        assertThat(response.customTime()).isEqualTo(LocalTime.of(9, 30));
        verify(rescheduleDoseLogsPort)
                .rescheduleFuturePending(SCHEDULE_ID, LocalTime.of(9, 30), LocalDate.of(2026, 5, 28));
    }

    @Test
    @DisplayName("기간 연장 + customTime 동시 변경 → lock 은 새(연장된) endDate 기준 → 성공")
    void update_extendEndDateWithCustomTime_locksAgainstNewEndDate() {
        // given
        Schedule schedule = schedule(LocalDate.of(2026, 5, 31));
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(scheduleRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        UpdateScheduleRequest request =
                new UpdateScheduleRequest(null, LocalTime.of(9, 30), LocalDate.of(2026, 6, 30));

        // when — 오늘 KST 2026-06-02, 옛 endDate 05-31 이지만 연장된 06-30 기준이라 허용
        ScheduleResponse response = sutWith(AFTER_PERIOD).update(SCHEDULE_ID, request);

        // then
        assertThat(response.customTime()).isEqualTo(LocalTime.of(9, 30));
        assertThat(response.endDate()).isEqualTo(LocalDate.of(2026, 6, 30));
        verify(rescheduleDoseLogsPort)
                .rescheduleFuturePending(eq(SCHEDULE_ID), eq(LocalTime.of(9, 30)), any());
    }

    @Test
    @DisplayName("연장 없이 기간 종료 후 customTime 수정 → SCHEDULE_PERIOD_ENDED, 저장/재예약 X")
    void update_afterPeriod_throwsPeriodEnded() {
        // given
        Schedule schedule = schedule(END);
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        UpdateScheduleRequest request = new UpdateScheduleRequest(null, LocalTime.of(9, 30), null);

        // when / then
        assertThatThrownBy(() -> sutWith(AFTER_PERIOD).update(SCHEDULE_ID, request))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SCHEDULE_PERIOD_ENDED);
        assertThat(schedule.getCustomTime()).isEqualTo(LocalTime.of(8, 0));
        verify(scheduleRepository, never()).save(any());
        verify(rescheduleDoseLogsPort, never()).rescheduleFuturePending(any(), any(), any());
    }

    @Test
    @DisplayName("customTime null → 기존 customTime 유지, 재예약 위임 안 함")
    void update_nullCustomTime_keepsExistingAndNoReschedule() {
        // given
        Schedule schedule = schedule(END);
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(scheduleRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        UpdateScheduleRequest request = new UpdateScheduleRequest(TimeOfDay.EVENING, null, null);

        // when
        ScheduleResponse response = sutWith(WITHIN_PERIOD).update(SCHEDULE_ID, request);

        // then
        assertThat(schedule.getCustomTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(response.timeOfDay()).isEqualTo(TimeOfDay.EVENING);
        verify(rescheduleDoseLogsPort, never()).rescheduleFuturePending(any(), any(), any());
    }

    @Test
    @DisplayName("스케줄 미존재 → SCHEDULE_NOT_FOUND")
    void update_notFound_throws() {
        // given
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.empty());
        UpdateScheduleRequest request = new UpdateScheduleRequest(null, LocalTime.of(9, 30), null);

        // when / then
        assertThatThrownBy(() -> sutWith(WITHIN_PERIOD).update(SCHEDULE_ID, request))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SCHEDULE_NOT_FOUND);
    }

    @Test
    @DisplayName("처방전 소유자 아닌 요청자 수정 시도 → PATIENT_ACCESS_DENIED, 저장 X")
    void update_whenNotOwner_throwsPatientAccessDenied() {
        // given
        UserContext.set(99L);
        Schedule schedule = schedule(END);
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        doThrow(new PillmateException(ErrorCode.PATIENT_ACCESS_DENIED))
                .when(patientAccessGuard).requireAccess(99L, 2L);

        // when / then
        assertThatThrownBy(() -> sutWith(WITHIN_PERIOD).update(SCHEDULE_ID,
                new UpdateScheduleRequest(null, LocalTime.of(9, 30), null)))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PATIENT_ACCESS_DENIED);
        verify(scheduleRepository, never()).save(any());
    }
}
