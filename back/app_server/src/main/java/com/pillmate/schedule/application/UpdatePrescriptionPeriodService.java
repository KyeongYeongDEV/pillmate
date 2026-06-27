package com.pillmate.schedule.application;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.common.security.PatientAccessGuard;
import com.pillmate.common.security.UserContext;
import com.pillmate.schedule.application.port.PeriodAdjustDoseLogsPort;
import com.pillmate.schedule.domain.model.Schedule;
import com.pillmate.schedule.domain.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UpdatePrescriptionPeriodService implements UpdatePrescriptionPeriodUseCase {

    private static final int MAX_DAYS_AHEAD = 365;

    private final ScheduleRepository scheduleRepository;
    private final PeriodAdjustDoseLogsPort periodAdjustDoseLogsPort;
    private final PatientAccessGuard patientAccessGuard;
    private final Clock clock;

    @Override
    @Transactional
    public void update(Long prescriptionId, LocalDate newEndDate) {
        List<Schedule> slots = requireActiveSlots(prescriptionId);
        Long patientId = slots.get(0).getPatientId();
        patientAccessGuard.requireAccess(UserContext.get(), patientId);
        validateUpperBound(newEndDate);
        validatePerSlotPeriod(slots, newEndDate);
        adjustSlots(slots, patientId, newEndDate);
    }

    private List<Schedule> requireActiveSlots(Long prescriptionId) {
        List<Schedule> slots = scheduleRepository.findActiveByPrescriptionId(prescriptionId);
        if (slots.isEmpty()) {
            throw new PillmateException(ErrorCode.SCHEDULE_NOT_FOUND);
        }
        return slots;
    }

    private void validateUpperBound(LocalDate newEndDate) {
        LocalDate limit = LocalDate.now(clock).plusDays(MAX_DAYS_AHEAD);
        if (newEndDate.isAfter(limit)) {
            throw new PillmateException(ErrorCode.INVALID_REQUEST);
        }
    }

    private void validatePerSlotPeriod(List<Schedule> slots, LocalDate newEndDate) {
        for (Schedule slot : slots) {
            if (newEndDate.isBefore(slot.getStartDate())) {
                throw new PillmateException(ErrorCode.SCHEDULE_INVALID_PERIOD);
            }
        }
    }

    private void adjustSlots(List<Schedule> slots, Long patientId, LocalDate newEndDate) {
        for (Schedule slot : slots) {
            LocalDate oldEndDate = slot.getEndDate();
            slot.updateEndDate(newEndDate);
            scheduleRepository.save(slot);
            adjustDoseLogs(slot, patientId, oldEndDate, newEndDate);
        }
    }

    private void adjustDoseLogs(Schedule slot, Long patientId,
                                LocalDate oldEndDate, LocalDate newEndDate) {
        if (newEndDate.isAfter(oldEndDate)) {
            periodAdjustDoseLogsPort.createLogsForRange(
                    slot.getId(), patientId, slot.getCustomTime(),
                    oldEndDate.plusDays(1), newEndDate);
        } else if (newEndDate.isBefore(oldEndDate)) {
            periodAdjustDoseLogsPort.skipPendingAfter(slot.getId(), newEndDate);
        }
    }
}
