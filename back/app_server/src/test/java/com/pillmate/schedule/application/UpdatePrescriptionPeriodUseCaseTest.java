package com.pillmate.schedule.application;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.common.security.PatientAccessGuard;
import com.pillmate.common.security.UserContext;
import com.pillmate.schedule.application.port.PeriodAdjustDoseLogsPort;
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
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("UpdatePrescriptionPeriodUseCase — 처방 단위 복약 기간 변경")
class UpdatePrescriptionPeriodUseCaseTest {

    private static final Long PATIENT_ID = 7L;
    private static final Long OTHER_ID = 99L;
    private static final Long PRESCRIPTION_ID = 10L;
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 1);
    private static final Clock FIXED_CLOCK =
            Clock.fixed(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);

    @Mock ScheduleRepository scheduleRepository;
    @Mock PeriodAdjustDoseLogsPort periodAdjustDoseLogsPort;

    private UpdatePrescriptionPeriodService sut;

    @BeforeEach
    void setUp() {
        UserContext.set(PATIENT_ID);
        sut = new UpdatePrescriptionPeriodService(
                scheduleRepository, periodAdjustDoseLogsPort, new PatientAccessGuard(), FIXED_CLOCK);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("기간 연장 → 모든 활성 슬롯 endDate 갱신 + 새 구간 dose_log 생성")
    void update_extends_updatesAllSlotsAndCreatesLogs() {
        LocalDate oldEnd = LocalDate.of(2026, 7, 10);
        LocalDate newEnd = LocalDate.of(2026, 7, 20);
        Schedule slot1 = activeSlot(PRESCRIPTION_ID, PATIENT_ID, TODAY, oldEnd, TimeOfDay.MORNING);
        Schedule slot2 = activeSlot(PRESCRIPTION_ID, PATIENT_ID, TODAY, oldEnd, TimeOfDay.EVENING);
        given(scheduleRepository.findActiveByPrescriptionId(PRESCRIPTION_ID))
                .willReturn(List.of(slot1, slot2));
        given(scheduleRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        sut.update(PRESCRIPTION_ID, newEnd);

        assertThat(slot1.getEndDate()).isEqualTo(newEnd);
        assertThat(slot2.getEndDate()).isEqualTo(newEnd);
        verify(periodAdjustDoseLogsPort).createLogsForRange(
                slot1.getId(), PATIENT_ID, slot1.getCustomTime(),
                oldEnd.plusDays(1), newEnd);
        verify(periodAdjustDoseLogsPort).createLogsForRange(
                slot2.getId(), PATIENT_ID, slot2.getCustomTime(),
                oldEnd.plusDays(1), newEnd);
    }

    @Test
    @DisplayName("기간 단축 → 모든 활성 슬롯 endDate 갱신 + 미래 PENDING dose_log 비활성")
    void update_shortens_updatesAllSlotsAndSkipsLogs() {
        LocalDate oldEnd = LocalDate.of(2026, 7, 20);
        LocalDate newEnd = LocalDate.of(2026, 7, 10);
        Schedule slot1 = activeSlot(PRESCRIPTION_ID, PATIENT_ID, TODAY, oldEnd, TimeOfDay.MORNING);
        given(scheduleRepository.findActiveByPrescriptionId(PRESCRIPTION_ID))
                .willReturn(List.of(slot1));
        given(scheduleRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        sut.update(PRESCRIPTION_ID, newEnd);

        assertThat(slot1.getEndDate()).isEqualTo(newEnd);
        verify(periodAdjustDoseLogsPort).skipPendingAfter(slot1.getId(), newEnd);
        verify(periodAdjustDoseLogsPort, never())
                .createLogsForRange(anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("newEndDate == currentEndDate → 슬롯 저장만(dose_log 생성/취소 없음)")
    void update_sameEndDate_onlySaves() {
        LocalDate sameEnd = LocalDate.of(2026, 7, 10);
        Schedule slot1 = activeSlot(PRESCRIPTION_ID, PATIENT_ID, TODAY, sameEnd, TimeOfDay.MORNING);
        given(scheduleRepository.findActiveByPrescriptionId(PRESCRIPTION_ID))
                .willReturn(List.of(slot1));
        given(scheduleRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        sut.update(PRESCRIPTION_ID, sameEnd);

        verify(periodAdjustDoseLogsPort, never()).createLogsForRange(anyLong(), anyLong(), any(), any(), any());
        verify(periodAdjustDoseLogsPort, never()).skipPendingAfter(anyLong(), any());
    }

    @Test
    @DisplayName("타인 처방 슬롯 변경 시도 → PATIENT_ACCESS_DENIED")
    void update_otherPatient_throwsAccessDenied() {
        Schedule slot = activeSlot(PRESCRIPTION_ID, OTHER_ID, TODAY, LocalDate.of(2026, 7, 10), TimeOfDay.MORNING);
        given(scheduleRepository.findActiveByPrescriptionId(PRESCRIPTION_ID))
                .willReturn(List.of(slot));

        assertThatThrownBy(() -> sut.update(PRESCRIPTION_ID, LocalDate.of(2026, 7, 20)))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PATIENT_ACCESS_DENIED);

        verify(scheduleRepository, never()).save(any());
    }

    @Test
    @DisplayName("활성 슬롯 없음 → SCHEDULE_NOT_FOUND")
    void update_noActiveSlots_throwsScheduleNotFound() {
        given(scheduleRepository.findActiveByPrescriptionId(PRESCRIPTION_ID))
                .willReturn(List.of());

        assertThatThrownBy(() -> sut.update(PRESCRIPTION_ID, LocalDate.of(2026, 7, 20)))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SCHEDULE_NOT_FOUND);
    }

    @Test
    @DisplayName("newEndDate < 슬롯 startDate → SCHEDULE_INVALID_PERIOD")
    void update_newEndBeforeSlotStart_throwsInvalidPeriod() {
        LocalDate startDate = LocalDate.of(2026, 7, 5);
        LocalDate newEnd = LocalDate.of(2026, 7, 3);
        Schedule slot = activeSlot(PRESCRIPTION_ID, PATIENT_ID, startDate, LocalDate.of(2026, 7, 20), TimeOfDay.MORNING);
        given(scheduleRepository.findActiveByPrescriptionId(PRESCRIPTION_ID))
                .willReturn(List.of(slot));

        assertThatThrownBy(() -> sut.update(PRESCRIPTION_ID, newEnd))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SCHEDULE_INVALID_PERIOD);
    }

    @Test
    @DisplayName("newEndDate가 오늘 + 365일 초과 → INVALID_REQUEST (dose_log 폭증 방지)")
    void update_endDateTooFar_throwsInvalidRequest() {
        LocalDate tooFar = TODAY.plusDays(366);
        Schedule slot = activeSlot(PRESCRIPTION_ID, PATIENT_ID, TODAY, LocalDate.of(2026, 7, 10), TimeOfDay.MORNING);
        given(scheduleRepository.findActiveByPrescriptionId(PRESCRIPTION_ID))
                .willReturn(List.of(slot));

        assertThatThrownBy(() -> sut.update(PRESCRIPTION_ID, tooFar))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REQUEST);
    }

    private static final java.util.concurrent.atomic.AtomicLong ID_SEQ = new java.util.concurrent.atomic.AtomicLong(1);

    private Schedule activeSlot(Long prescriptionId, Long patientId,
                                LocalDate startDate, LocalDate endDate, TimeOfDay timeOfDay) {
        Schedule s = Schedule.forPrescription(1L, patientId, prescriptionId,
                timeOfDay, null, startDate, endDate, patientId);
        ReflectionTestUtils.setField(s, "id", ID_SEQ.getAndIncrement());
        return s;
    }
}
