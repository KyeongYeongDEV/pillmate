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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DisplayName("RemovePrescriptionSlotUseCase — 약봉투 시간슬롯 삭제(전 약 deactivate)")
@ExtendWith(MockitoExtension.class)
class RemovePrescriptionSlotUseCaseTest {

    private static final LocalDate START = LocalDate.of(2026, 6, 21);
    private static final LocalDate END = LocalDate.of(2026, 6, 27);

    private final ScheduleRepository scheduleRepository = mock(ScheduleRepository.class);
    private final CareGroupGuard careGroupGuard = mock(CareGroupGuard.class);
    private final PatientAccessGuard patientAccessGuard = new PatientAccessGuard();
    private final RemovePrescriptionSlotUseCase sut =
            new RemovePrescriptionSlotUseCase(scheduleRepository, careGroupGuard, patientAccessGuard);

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
