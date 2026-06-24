package com.pillmate.schedule.application;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.common.security.UserContext;
import com.pillmate.prescription.application.port.PrescriptionLookupPort;
import com.pillmate.prescription.application.port.PrescriptionLookupPort.PrescriptionOwner;
import com.pillmate.schedule.application.port.PrescriptionSchedulePort.CreatePrescriptionSchedulesCommand;
import com.pillmate.schedule.application.port.PrescriptionSchedulePort.CreatedSchedule;
import com.pillmate.schedule.domain.model.Schedule;
import com.pillmate.schedule.domain.model.TimeOfDay;
import com.pillmate.schedule.domain.repository.ScheduleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("AddPrescriptionSlotUseCase — 약봉투에 처방전 단위 시간슬롯 추가")
@ExtendWith(MockitoExtension.class)
class AddPrescriptionSlotUseCaseTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 24);
    private static final LocalDate START = LocalDate.of(2026, 6, 21);
    private static final LocalDate END = LocalDate.of(2026, 6, 27);

    @Mock private Clock clock;

    private final ScheduleRepository scheduleRepository = mock(ScheduleRepository.class);
    private final PrescriptionScheduleService prescriptionScheduleService = mock(PrescriptionScheduleService.class);
    private final PrescriptionLookupPort prescriptionLookupPort = mock(PrescriptionLookupPort.class);
    private AddPrescriptionSlotUseCase sut;

    @BeforeEach
    void setUp() {
        given(clock.instant()).willReturn(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant());
        given(clock.getZone()).willReturn(ZoneOffset.UTC);
        sut = new AddPrescriptionSlotUseCase(scheduleRepository, prescriptionScheduleService, prescriptionLookupPort, clock);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("기존 스케줄 있을 때 — 그룹·환자·기간(sample)으로 새 슬롯 생성을 위임한다")
    void addSlot_delegatesWithGroupPatientPeriod() {
        UserContext.set(2L);
        given(prescriptionLookupPort.findOwner(99L)).willReturn(Optional.of(new PrescriptionOwner(2L, 1L, START, 7)));
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
    @DisplayName("스케줄 0개인 활성 처방 — 처방 기간으로 첫 슬롯 생성 성공")
    void addSlot_noExistingSchedules_activePrescription_createsFirstSlot() {
        UserContext.set(2L);
        // prescribedAt=START(2026-06-21), maxDurationDays=7 → endDate=2026-06-27, today=2026-06-24 (not expired)
        given(prescriptionLookupPort.findOwner(99L)).willReturn(Optional.of(new PrescriptionOwner(2L, 1L, START, 7)));
        given(scheduleRepository.findActiveByPrescriptionId(99L)).willReturn(List.of());
        given(prescriptionScheduleService.createForPrescription(any(CreatePrescriptionSchedulesCommand.class)))
                .willReturn(List.of(new CreatedSchedule(5L, "MORNING", LocalTime.of(8, 0), START, START.plusDays(6))));

        List<CreatedSchedule> created = sut.addSlot(99L, TimeOfDay.MORNING, null);

        ArgumentCaptor<CreatePrescriptionSchedulesCommand> captor =
                ArgumentCaptor.forClass(CreatePrescriptionSchedulesCommand.class);
        verify(prescriptionScheduleService).createForPrescription(captor.capture());
        CreatePrescriptionSchedulesCommand sent = captor.getValue();
        assertThat(sent.startDate()).isEqualTo(START);
        assertThat(sent.endDate()).isEqualTo(START.plusDays(6));  // prescribedAt + (7-1)
        assertThat(created).hasSize(1);
    }

    @Test
    @DisplayName("스케줄 0개인 만료 처방 — PRESCRIPTION_PERIOD_ENDED 거부")
    void addSlot_noExistingSchedules_expiredPrescription_throwsPeriodEnded() {
        LocalDate oldDate = LocalDate.of(2026, 6, 1);
        // prescribedAt=2026-06-01, maxDurationDays=7 → endDate=2026-06-07 (< today 2026-06-24)
        given(prescriptionLookupPort.findOwner(99L)).willReturn(Optional.of(new PrescriptionOwner(2L, 1L, oldDate, 7)));
        given(scheduleRepository.findActiveByPrescriptionId(99L)).willReturn(List.of());

        assertThatThrownBy(() -> sut.addSlot(99L, TimeOfDay.MORNING, null))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRESCRIPTION_PERIOD_ENDED);
    }

    @Test
    @DisplayName("기존 스케줄 있지만 만료된 처방 — PRESCRIPTION_PERIOD_ENDED 거부")
    void addSlot_existingSchedules_expiredPrescription_throwsPeriodEnded() {
        LocalDate expiredEnd = LocalDate.of(2026, 6, 1);  // < today 2026-06-24
        given(prescriptionLookupPort.findOwner(99L)).willReturn(Optional.of(new PrescriptionOwner(2L, 1L, START, 7)));
        Schedule expiredSchedule = Schedule.forPrescription(1L, 2L, 99L, TimeOfDay.MORNING, null, START, expiredEnd, 2L);
        given(scheduleRepository.findActiveByPrescriptionId(99L)).willReturn(List.of(expiredSchedule));

        assertThatThrownBy(() -> sut.addSlot(99L, TimeOfDay.BEDTIME, null))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRESCRIPTION_PERIOD_ENDED);
    }

    @Test
    @DisplayName("기존 스케줄 있을 때 — careGroupId/patientId를 스케줄(sample)에서 가져온다")
    void addSlot_existingSchedules_careGroupAndPatientFromSample() {
        UserContext.set(1L);
        // 처방 owner: careGroupId=NULL (개인), patientId=1
        given(prescriptionLookupPort.findOwner(99L)).willReturn(Optional.of(new PrescriptionOwner(1L, null, START, 7)));
        // 스케줄은 careGroupId=2, patientId=1 로 저장돼 있음 (기존 데이터)
        Schedule sampleWithGroup = Schedule.forPrescription(2L, 1L, 99L, TimeOfDay.MORNING, null, START, END, 1L);
        given(scheduleRepository.findActiveByPrescriptionId(99L)).willReturn(List.of(sampleWithGroup));
        given(prescriptionScheduleService.createForPrescription(any(CreatePrescriptionSchedulesCommand.class)))
                .willReturn(List.of());

        sut.addSlot(99L, TimeOfDay.BEDTIME, LocalTime.of(22, 0));

        ArgumentCaptor<CreatePrescriptionSchedulesCommand> captor =
                ArgumentCaptor.forClass(CreatePrescriptionSchedulesCommand.class);
        verify(prescriptionScheduleService).createForPrescription(captor.capture());
        assertThat(captor.getValue().careGroupId()).isEqualTo(2L);  // sample, not owner(null)
        assertThat(captor.getValue().patientId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("스케줄 0개 + 개인처방(careGroupId null) — careGroupId=null 그대로 넘긴다")
    void addSlot_noExistingSchedules_personalPrescription_nullCareGroupId() {
        UserContext.set(1L);
        // 처방 owner: careGroupId=NULL, patientId=1
        given(prescriptionLookupPort.findOwner(99L)).willReturn(Optional.of(new PrescriptionOwner(1L, null, START, 7)));
        given(scheduleRepository.findActiveByPrescriptionId(99L)).willReturn(List.of());
        given(prescriptionScheduleService.createForPrescription(any(CreatePrescriptionSchedulesCommand.class)))
                .willReturn(List.of(new CreatedSchedule(5L, "MORNING", LocalTime.of(8, 0), START, START.plusDays(6))));

        sut.addSlot(99L, TimeOfDay.MORNING, null);

        ArgumentCaptor<CreatePrescriptionSchedulesCommand> captor =
                ArgumentCaptor.forClass(CreatePrescriptionSchedulesCommand.class);
        verify(prescriptionScheduleService).createForPrescription(captor.capture());
        assertThat(captor.getValue().careGroupId()).isNull();   // personal → null
        assertThat(captor.getValue().patientId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("동일 처방전에 이미 같은 timeOfDay(기본 시각) 슬롯 있으면 SCHEDULE_CONFLICT")
    void addSlot_whenSameDefaultTimeExists_throws() {
        given(prescriptionLookupPort.findOwner(99L)).willReturn(Optional.of(new PrescriptionOwner(2L, 1L, START, 7)));
        given(scheduleRepository.findActiveByPrescriptionId(99L)).willReturn(
                List.of(schedule(TimeOfDay.MORNING)));  // MORNING = 08:00

        assertThatThrownBy(() -> sut.addSlot(99L, TimeOfDay.MORNING, null))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SCHEDULE_CONFLICT);
    }

    @Test
    @DisplayName("동일 처방전에 이미 같은 customTime 슬롯 있으면 SCHEDULE_CONFLICT")
    void addSlot_whenSameCustomTimeExists_throws() {
        given(prescriptionLookupPort.findOwner(99L)).willReturn(Optional.of(new PrescriptionOwner(2L, 1L, START, 7)));
        Schedule existingWithCustomTime =
                Schedule.forPrescription(1L, 2L, 99L, TimeOfDay.NOON, LocalTime.of(9, 0), START, END, 2L);
        given(scheduleRepository.findActiveByPrescriptionId(99L)).willReturn(List.of(existingWithCustomTime));

        assertThatThrownBy(() -> sut.addSlot(99L, TimeOfDay.MORNING, LocalTime.of(9, 0)))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SCHEDULE_CONFLICT);
    }

    private Schedule schedule(TimeOfDay timeOfDay) {
        return Schedule.forPrescription(1L, 2L, 99L, timeOfDay, null, START, END, 2L);
    }
}
