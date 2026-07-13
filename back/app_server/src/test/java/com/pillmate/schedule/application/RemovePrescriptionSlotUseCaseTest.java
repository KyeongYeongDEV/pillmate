package com.pillmate.schedule.application;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.common.security.CareGroupGuard;
import com.pillmate.common.security.PatientAccessGuard;
import com.pillmate.common.security.UserContext;
import com.pillmate.schedule.domain.model.Schedule;
import com.pillmate.schedule.domain.model.TimeOfDay;
import com.pillmate.schedule.domain.repository.ScheduleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DisplayName("RemovePrescriptionSlotUseCase — 약봉투 시간슬롯 삭제(전 약 deactivate)")
@ExtendWith(MockitoExtension.class)
class RemovePrescriptionSlotUseCaseTest {

    private static final LocalDate START = LocalDate.of(2026, 6, 21);
    private static final LocalDate END = LocalDate.of(2026, 6, 27);

    private static final java.time.Instant FIXED_NOW = java.time.Instant.parse("2026-07-13T10:00:00Z");

    private final ScheduleRepository scheduleRepository = mock(ScheduleRepository.class);
    private final CareGroupGuard careGroupGuard = mock(CareGroupGuard.class);
    private final PatientAccessGuard patientAccessGuard = new PatientAccessGuard();
    private final com.pillmate.schedule.application.port.PeriodAdjustDoseLogsPort periodAdjustDoseLogsPort =
            mock(com.pillmate.schedule.application.port.PeriodAdjustDoseLogsPort.class);
    private final java.time.Clock clock = java.time.Clock.fixed(FIXED_NOW, java.time.ZoneOffset.UTC);
    private final RemovePrescriptionSlotUseCase sut = new RemovePrescriptionSlotUseCase(
            scheduleRepository, careGroupGuard, patientAccessGuard, periodAdjustDoseLogsPort, clock);

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("지정 슬롯의 모든 약 스케줄만 deactivate, 다른 슬롯은 보존")
    void removeSlot_deactivatesOnlyTargetSlot() {
        UserContext.set(2L);
        given(scheduleRepository.findActiveByPrescriptionId(99L)).willReturn(List.of(
                schedule(10L, TimeOfDay.MORNING), schedule(20L, TimeOfDay.MORNING),
                schedule(10L, TimeOfDay.EVENING)));
        given(scheduleRepository.save(any(Schedule.class))).willAnswer(inv -> inv.getArgument(0));

        int removed = sut.removeSlot(99L, TimeOfDay.MORNING);

        verify(careGroupGuard).requireAccessible(1L);
        ArgumentCaptor<Schedule> captor = ArgumentCaptor.forClass(Schedule.class);
        verify(scheduleRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).allMatch(s -> !s.isActive());
        assertThat(captor.getAllValues()).allMatch(s -> s.getTimeOfDay() == TimeOfDay.MORNING);
        assertThat(removed).isEqualTo(2);
    }

    @Test
    @DisplayName("T-BE-REMINDER-FIX P1-2: 슬롯 삭제 시 대상 스케줄들의 현재 시각 이후 PENDING dose_log skip")
    void removeSlot_skipsFuturePendingDoseLogsOfRemovedSchedules() {
        UserContext.set(2L);
        Schedule morning1 = schedule(10L, TimeOfDay.MORNING);
        Schedule morning2 = schedule(20L, TimeOfDay.MORNING);
        org.springframework.test.util.ReflectionTestUtils.setField(morning1, "id", 101L);
        org.springframework.test.util.ReflectionTestUtils.setField(morning2, "id", 102L);
        given(scheduleRepository.findActiveByPrescriptionId(99L)).willReturn(List.of(
                morning1, morning2, schedule(10L, TimeOfDay.EVENING)));
        given(scheduleRepository.save(any(Schedule.class))).willAnswer(inv -> inv.getArgument(0));

        sut.removeSlot(99L, TimeOfDay.MORNING);

        verify(periodAdjustDoseLogsPort).skipPendingFrom(101L, FIXED_NOW);
        verify(periodAdjustDoseLogsPort).skipPendingFrom(102L, FIXED_NOW);
        verify(periodAdjustDoseLogsPort, times(2)).skipPendingFrom(anyLong(), any());
    }

    @Test
    @DisplayName("T-BE-SOLO-SCHEDULE-GUARD-FIX: 솔로(careGroupId null) 본인 → 그룹 가드 스킵하고 슬롯 삭제 성공")
    void removeSlot_soloScheduleOwner_succeedsWithoutGroupGuard() {
        UserContext.set(2L);
        Schedule solo = Schedule.of(null, 2L, 10L, 99L, TimeOfDay.MORNING, null, START, END, 2L);
        given(scheduleRepository.findActiveByPrescriptionId(99L)).willReturn(List.of(solo));
        given(scheduleRepository.save(any(Schedule.class))).willAnswer(inv -> inv.getArgument(0));

        int removed = sut.removeSlot(99L, TimeOfDay.MORNING);

        assertThat(removed).isEqualTo(1);
        verify(careGroupGuard, org.mockito.Mockito.never()).requireAccessible(any());
    }

    @Test
    @DisplayName("T-BE-SOLO-SCHEDULE-GUARD-FIX: 솔로 스케줄 타인 → PATIENT_ACCESS_DENIED (무가드 경로 없음)")
    void removeSlot_soloScheduleNotOwner_denied() {
        UserContext.set(99L);
        Schedule solo = Schedule.of(null, 2L, 10L, 99L, TimeOfDay.MORNING, null, START, END, 2L);
        given(scheduleRepository.findActiveByPrescriptionId(99L)).willReturn(List.of(solo));

        assertThatThrownBy(() -> sut.removeSlot(99L, TimeOfDay.MORNING))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PATIENT_ACCESS_DENIED);
        verify(scheduleRepository, org.mockito.Mockito.never()).save(any(Schedule.class));
    }

    @Test
    @DisplayName("요청 사용자가 처방전 소유자가 아니면 PATIENT_ACCESS_DENIED (P0-3)")
    void removeSlot_whenNotOwner_throws() {
        UserContext.set(5L);
        given(scheduleRepository.findActiveByPrescriptionId(99L)).willReturn(List.of(
                schedule(10L, TimeOfDay.MORNING)));

        assertThatThrownBy(() -> sut.removeSlot(99L, TimeOfDay.MORNING))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PATIENT_ACCESS_DENIED);
    }

    @Test
    @DisplayName("약봉투에 활성 스케줄이 없으면 SCHEDULE_NOT_FOUND")
    void removeSlot_whenNoExistingSchedules_throws() {
        given(scheduleRepository.findActiveByPrescriptionId(99L)).willReturn(List.of());

        assertThatThrownBy(() -> sut.removeSlot(99L, TimeOfDay.MORNING))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SCHEDULE_NOT_FOUND);
    }

    private Schedule schedule(Long drugId, TimeOfDay timeOfDay) {
        return Schedule.of(1L, 2L, drugId, 99L, timeOfDay, null, START, END, 2L);
    }
}
