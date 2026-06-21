package com.pillmate.schedule.application;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.common.security.UserContext;
import com.pillmate.schedule.application.port.PrescriptionSchedulePort.CreatePrescriptionSchedulesCommand;
import com.pillmate.schedule.application.port.PrescriptionSchedulePort.CreatedSchedule;
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
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("AddPrescriptionSlotUseCase — 약봉투에 처방전 단위 시간슬롯 추가")
@ExtendWith(MockitoExtension.class)
class AddPrescriptionSlotUseCaseTest {

    private static final LocalDate START = LocalDate.of(2026, 6, 21);
    private static final LocalDate END = LocalDate.of(2026, 6, 27);

    private final ScheduleRepository scheduleRepository = mock(ScheduleRepository.class);
    private final PrescriptionScheduleService prescriptionScheduleService = mock(PrescriptionScheduleService.class);
    private final AddPrescriptionSlotUseCase sut =
            new AddPrescriptionSlotUseCase(scheduleRepository, prescriptionScheduleService);

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("기존 약봉투의 그룹·환자·기간으로 새 슬롯 1건 생성을 위임한다 (요청자=requesterId)")
    void addSlot_delegatesWithGroupPatientPeriod() {
        UserContext.set(2L);
        given(scheduleRepository.findActiveByPrescriptionId(99L)).willReturn(List.of(
                schedule(TimeOfDay.MORNING), schedule(TimeOfDay.NOON)));
        given(prescriptionScheduleService.createForPrescription(any(CreatePrescriptionSchedulesCommand.class)))
                .willReturn(List.of(new CreatedSchedule(5L, "BEDTIME", LocalTime.of(22, 0), START, END)));

        List<CreatedSchedule> created = sut.addSlot(99L, TimeOfDay.BEDTIME, LocalTime.of(22, 0));

        ArgumentCaptor<CreatePrescriptionSchedulesCommand> captor =
                ArgumentCaptor.forClass(CreatePrescriptionSchedulesCommand.class);
        verify(prescriptionScheduleService).createForPrescription(captor.capture());
        CreatePrescriptionSchedulesCommand sent = captor.getValue();
        assertThat(sent.prescriptionId()).isEqualTo(99L);
        assertThat(sent.careGroupId()).isEqualTo(1L);
        assertThat(sent.patientId()).isEqualTo(2L);
        assertThat(sent.requesterId()).isEqualTo(2L);
        assertThat(sent.slots()).hasSize(1);
        assertThat(sent.slots().get(0).timeOfDay()).isEqualTo("BEDTIME");
        assertThat(sent.startDate()).isEqualTo(START);
        assertThat(sent.endDate()).isEqualTo(END);
        assertThat(created).hasSize(1);
    }

    @Test
    @DisplayName("약봉투에 활성 스케줄이 없으면 SCHEDULE_NOT_FOUND")
    void addSlot_whenNoExistingSchedules_throws() {
        given(scheduleRepository.findActiveByPrescriptionId(99L)).willReturn(List.of());

        assertThatThrownBy(() -> sut.addSlot(99L, TimeOfDay.BEDTIME, null))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SCHEDULE_NOT_FOUND);
    }

    @Test
    @DisplayName("동일 처방전에 이미 같은 timeOfDay(기본 시각) 슬롯 있으면 SCHEDULE_CONFLICT")
    void addSlot_whenSameDefaultTimeExists_throws() {
        given(scheduleRepository.findActiveByPrescriptionId(99L)).willReturn(
                List.of(schedule(TimeOfDay.MORNING)));  // MORNING = 08:00

        assertThatThrownBy(() -> sut.addSlot(99L, TimeOfDay.MORNING, null))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SCHEDULE_CONFLICT);
    }

    @Test
    @DisplayName("동일 처방전에 이미 같은 customTime 슬롯 있으면 SCHEDULE_CONFLICT")
    void addSlot_whenSameCustomTimeExists_throws() {
        // 기존 슬롯: NOON 시간대이지만 customTime=09:00
        Schedule existingWithCustomTime =
                Schedule.forPrescription(1L, 2L, 99L, TimeOfDay.NOON, LocalTime.of(9, 0), START, END, 2L);
        given(scheduleRepository.findActiveByPrescriptionId(99L)).willReturn(List.of(existingWithCustomTime));

        // 다른 timeOfDay지만 같은 customTime 09:00 으로 추가 시도
        assertThatThrownBy(() -> sut.addSlot(99L, TimeOfDay.MORNING, LocalTime.of(9, 0)))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SCHEDULE_CONFLICT);
    }

    private Schedule schedule(TimeOfDay timeOfDay) {
        return Schedule.forPrescription(1L, 2L, 99L, timeOfDay, null, START, END, 2L);
    }
}
