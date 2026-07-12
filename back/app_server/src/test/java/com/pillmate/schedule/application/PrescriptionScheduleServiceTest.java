package com.pillmate.schedule.application;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.common.security.CareGroupGuard;
import com.pillmate.common.security.PatientAccessGuard;
import com.pillmate.schedule.application.port.PrescriptionSchedulePort.CreatePrescriptionSchedulesCommand;
import com.pillmate.schedule.application.port.PrescriptionSchedulePort.CreatedSchedule;
import com.pillmate.schedule.application.port.PrescriptionSchedulePort.SlotSpec;
import com.pillmate.schedule.domain.model.Schedule;
import com.pillmate.schedule.domain.model.TimeOfDay;
import com.pillmate.schedule.domain.repository.ScheduleRepository;
import com.pillmate.schedule.domain.service.ScheduleConflictChecker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.BDDMockito.willThrow;

@DisplayName("PrescriptionScheduleService — 약봉투(처방전) 단위 시간슬롯 스케줄 생성")
@ExtendWith(MockitoExtension.class)
class PrescriptionScheduleServiceTest {

    private static final LocalDate START = LocalDate.of(2026, 6, 21);
    private static final LocalDate END = LocalDate.of(2026, 6, 27);

    private final ScheduleRepository scheduleRepository = mock(ScheduleRepository.class);
    private final ScheduleConflictChecker conflictChecker = mock(ScheduleConflictChecker.class);
    private final CareGroupGuard careGroupGuard = mock(CareGroupGuard.class);
    private final PatientAccessGuard patientAccessGuard = mock(PatientAccessGuard.class);

    private final PrescriptionScheduleService sut = new PrescriptionScheduleService(
            scheduleRepository, conflictChecker, careGroupGuard, patientAccessGuard);

    @Test
    @DisplayName("명시 슬롯 2개 → 약 곱 없이 처방전 단위 스케줄 2건, drugId null·prescriptionId 링크")
    void create_explicitSlots_createsOnePerSlot_notPerDrug() {
        given(scheduleRepository.findActiveByPrescriptionId(99L)).willReturn(List.of());
        given(scheduleRepository.save(any(Schedule.class))).willAnswer(inv -> inv.getArgument(0));

        List<CreatedSchedule> created = sut.createForPrescription(command(
                List.of(new SlotSpec("MORNING", LocalTime.of(8, 0)), new SlotSpec("EVENING", null))));

        verify(patientAccessGuard).requireAccess(2L, 2L);
        verify(careGroupGuard).requireAccessible(1L);
        ArgumentCaptor<Schedule> captor = ArgumentCaptor.forClass(Schedule.class);
        verify(scheduleRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).allMatch(s -> s.getDrugId() == null);
        assertThat(captor.getAllValues()).allMatch(s -> s.getPrescriptionId().equals(99L));
        assertThat(captor.getAllValues()).allMatch(s -> s.getCareGroupId().equals(1L) && s.getPatientId().equals(2L));
        assertThat(created).hasSize(2);
    }

    @Test
    @DisplayName("EVENING 슬롯 customTime 미지정 시 19:00 기본 시각으로 채운다")
    void create_slotWithoutCustomTime_usesDefaultTime() {
        given(scheduleRepository.findActiveByPrescriptionId(99L)).willReturn(List.of());
        given(scheduleRepository.save(any(Schedule.class))).willAnswer(inv -> inv.getArgument(0));

        sut.createForPrescription(command(List.of(new SlotSpec("EVENING", null))));

        ArgumentCaptor<Schedule> captor = ArgumentCaptor.forClass(Schedule.class);
        verify(scheduleRepository).save(captor.capture());
        assertThat(captor.getValue().getTimeOfDay()).isEqualTo(TimeOfDay.EVENING);
        assertThat(captor.getValue().getCustomTime()).isEqualTo(LocalTime.of(19, 0));
    }

    @Test
    @DisplayName("슬롯 미지정 → 아침08:00·점심12:30·저녁19:00 기본 슬롯(취침 제외)으로 생성")
    void create_noSlots_usesDefaultMorningNoonEvening() {
        given(scheduleRepository.findActiveByPrescriptionId(99L)).willReturn(List.of());
        given(scheduleRepository.save(any(Schedule.class))).willAnswer(inv -> inv.getArgument(0));

        sut.createForPrescription(command(null));

        ArgumentCaptor<Schedule> captor = ArgumentCaptor.forClass(Schedule.class);
        verify(scheduleRepository, times(3)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(Schedule::getTimeOfDay)
                .containsExactly(TimeOfDay.MORNING, TimeOfDay.NOON, TimeOfDay.EVENING);
        assertThat(captor.getAllValues()).extracting(Schedule::getCustomTime)
                .containsExactly(LocalTime.of(8, 0), LocalTime.of(12, 30), LocalTime.of(19, 0));
    }

    @Test
    @DisplayName("같은 처방전 같은 정확 시각이 이미 있으면 건너뛰고 나머지만 생성")
    void create_whenSlotConflicts_skipsConflictingSlot() {
        given(scheduleRepository.findActiveByPrescriptionId(99L)).willReturn(List.of());
        given(scheduleRepository.save(any(Schedule.class))).willAnswer(inv -> inv.getArgument(0));
        given(conflictChecker.hasPrescriptionSlotConflict(eq(99L), eq(LocalTime.of(8, 0)), any(), any(), anyList()))
                .willReturn(true);
        given(conflictChecker.hasPrescriptionSlotConflict(eq(99L), eq(LocalTime.of(19, 0)), any(), any(), anyList()))
                .willReturn(false);

        List<CreatedSchedule> created = sut.createForPrescription(command(
                List.of(new SlotSpec("MORNING", null), new SlotSpec("EVENING", null))));

        verify(scheduleRepository, times(1)).save(any(Schedule.class));
        assertThat(created).hasSize(1);
        assertThat(created.get(0).timeOfDay()).isEqualTo("EVENING");
    }

    @Test
    @DisplayName("한 처방전에 같은 bucket(MORNING) 다른 정확 시각 08:00·10:00 → 스케줄 2개 모두 저장 (유실 X)")
    void create_sameBucketDifferentExactTime_savesBoth() {
        given(scheduleRepository.findActiveByPrescriptionId(99L)).willReturn(List.of());
        given(scheduleRepository.save(any(Schedule.class))).willAnswer(inv -> inv.getArgument(0));
        given(conflictChecker.hasPrescriptionSlotConflict(any(), any(LocalTime.class), any(), any(), anyList()))
                .willReturn(false);

        List<CreatedSchedule> created = sut.createForPrescription(command(
                List.of(new SlotSpec("MORNING", LocalTime.of(8, 0)),
                        new SlotSpec("MORNING", LocalTime.of(10, 0)))));

        ArgumentCaptor<Schedule> captor = ArgumentCaptor.forClass(Schedule.class);
        verify(scheduleRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(Schedule::getCustomTime)
                .containsExactly(LocalTime.of(8, 0), LocalTime.of(10, 0));
        assertThat(created).hasSize(2);
    }

    @Test
    @DisplayName("동일 정확 시각 08:00·08:00 중복 요청 → 두 번째는 conflict skip 되어 1개만 생성")
    void create_sameExactTimeDuplicate_savesOnlyOne() {
        given(scheduleRepository.findActiveByPrescriptionId(99L)).willReturn(List.of());
        given(scheduleRepository.save(any(Schedule.class))).willAnswer(inv -> {
            Schedule s = inv.getArgument(0);
            given(conflictChecker.hasPrescriptionSlotConflict(
                    eq(99L), eq(LocalTime.of(8, 0)), any(), any(), anyList())).willReturn(true);
            return s;
        });

        List<CreatedSchedule> created = sut.createForPrescription(command(
                List.of(new SlotSpec("MORNING", LocalTime.of(8, 0)),
                        new SlotSpec("MORNING", LocalTime.of(8, 0)))));

        verify(scheduleRepository, times(1)).save(any(Schedule.class));
        assertThat(created).hasSize(1);
    }

    @Test
    @DisplayName("요청 사용자가 처방전 소유자가 아니면 거부하고 아무것도 생성하지 않는다 (P0-3)")
    void create_whenNotOwner_deniesAndCreatesNothing() {
        willThrow(new PillmateException(ErrorCode.PATIENT_ACCESS_DENIED))
                .given(patientAccessGuard).requireAccess(5L, 2L);

        assertThatThrownBy(() -> sut.createForPrescription(new CreatePrescriptionSchedulesCommand(
                1L, 2L, 99L, 5L, List.of(new SlotSpec("MORNING", null)), START, END)))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PATIENT_ACCESS_DENIED);

        verify(scheduleRepository, never()).save(any(Schedule.class));
    }

    @Test
    @DisplayName("careGroupId null(개인 처방) — 그룹 가드 호출 없이 스케줄 생성 허용")
    void create_whenCareGroupIdNull_skipsGroupGuard() {
        given(scheduleRepository.findActiveByPrescriptionId(99L)).willReturn(List.of());
        given(scheduleRepository.save(any(Schedule.class))).willAnswer(inv -> inv.getArgument(0));

        sut.createForPrescription(new CreatePrescriptionSchedulesCommand(
                null, 2L, 99L, 2L, List.of(new SlotSpec("MORNING", null)), START, END));

        verify(patientAccessGuard).requireAccess(2L, 2L);
        verify(careGroupGuard, never()).requireAccessible(any());
    }

    @Test
    @DisplayName("시작일이 종료일보다 늦으면 SCHEDULE_INVALID_PERIOD (P1, 400)")
    void create_whenStartAfterEnd_throwsInvalidPeriod() {
        assertThatThrownBy(() -> sut.createForPrescription(new CreatePrescriptionSchedulesCommand(
                1L, 2L, 99L, 2L, List.of(new SlotSpec("MORNING", null)), END, START)))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SCHEDULE_INVALID_PERIOD);

        verify(scheduleRepository, never()).save(any(Schedule.class));
    }

    @Test
    @DisplayName("잘못된 시간대 값이면 SCHEDULE_INVALID_TIME_OF_DAY (P1, 400)")
    void create_whenInvalidTimeOfDay_throwsInvalidTimeOfDay() {
        given(scheduleRepository.findActiveByPrescriptionId(99L)).willReturn(List.of());

        assertThatThrownBy(() -> sut.createForPrescription(command(List.of(new SlotSpec("DAWN", null)))))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SCHEDULE_INVALID_TIME_OF_DAY);

        verify(scheduleRepository, never()).save(any(Schedule.class));
    }

    private CreatePrescriptionSchedulesCommand command(List<SlotSpec> slots) {
        return new CreatePrescriptionSchedulesCommand(1L, 2L, 99L, 2L, slots, START, END);
    }
}
