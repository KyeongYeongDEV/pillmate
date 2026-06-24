package com.pillmate.schedule.application;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.common.security.UserContext;
import com.pillmate.prescription.application.port.PrescriptionLookupPort;
import com.pillmate.prescription.application.port.PrescriptionLookupPort.PrescriptionOwner;
import com.pillmate.schedule.application.port.PrescriptionSchedulePort.CreatePrescriptionSchedulesCommand;
import com.pillmate.schedule.application.port.PrescriptionSchedulePort.CreatedSchedule;
import com.pillmate.schedule.application.port.PrescriptionSchedulePort.SlotSpec;
import com.pillmate.schedule.domain.model.Schedule;
import com.pillmate.schedule.domain.model.TimeOfDay;
import com.pillmate.schedule.domain.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AddPrescriptionSlotUseCase {

    private static final int DEFAULT_DURATION_DAYS = 7;

    private final ScheduleRepository scheduleRepository;
    private final PrescriptionScheduleService prescriptionScheduleService;
    private final PrescriptionLookupPort prescriptionLookupPort;
    private final Clock clock;

    @Transactional
    public List<CreatedSchedule> addSlot(Long prescriptionId, TimeOfDay timeOfDay, LocalTime customTime) {
        PrescriptionOwner owner = requireOwner(prescriptionId);
        List<Schedule> existing = scheduleRepository.findActiveByPrescriptionId(prescriptionId);
        LocalDate startDate = resolveStart(existing, owner);
        LocalDate endDate = resolveEnd(existing, owner, startDate);
        requireNotExpired(endDate);
        requireNoTimeConflict(existing, effectiveTime(timeOfDay, customTime));
        return prescriptionScheduleService.createForPrescription(
                buildCommand(prescriptionId, owner, startDate, endDate, timeOfDay, customTime));
    }

    private PrescriptionOwner requireOwner(Long prescriptionId) {
        return prescriptionLookupPort.findOwner(prescriptionId)
                .orElseThrow(() -> new PillmateException(ErrorCode.PRESCRIPTION_NOT_FOUND));
    }

    private LocalDate resolveStart(List<Schedule> existing, PrescriptionOwner owner) {
        return existing.isEmpty() ? owner.prescribedAt() : existing.get(0).getStartDate();
    }

    private LocalDate resolveEnd(List<Schedule> existing, PrescriptionOwner owner, LocalDate start) {
        if (!existing.isEmpty()) return existing.get(0).getEndDate();
        int duration = owner.maxDurationDays() > 0 ? owner.maxDurationDays() : DEFAULT_DURATION_DAYS;
        return start.plusDays(duration - 1);
    }

    private void requireNotExpired(LocalDate endDate) {
        if (LocalDate.now(clock).isAfter(endDate)) {
            throw new PillmateException(ErrorCode.PRESCRIPTION_PERIOD_ENDED);
        }
    }

    private LocalTime effectiveTime(TimeOfDay timeOfDay, LocalTime customTime) {
        return customTime != null ? customTime : timeOfDay.defaultTime();
    }

    private void requireNoTimeConflict(List<Schedule> existing, LocalTime newTime) {
        boolean conflict = existing.stream().anyMatch(s -> newTime.equals(s.getCustomTime()));
        if (conflict) {
            throw new PillmateException(ErrorCode.SCHEDULE_CONFLICT);
        }
    }

    private CreatePrescriptionSchedulesCommand buildCommand(
            Long prescriptionId, PrescriptionOwner owner,
            LocalDate startDate, LocalDate endDate, TimeOfDay timeOfDay, LocalTime customTime) {
        return new CreatePrescriptionSchedulesCommand(
                owner.careGroupId(), owner.patientId(), prescriptionId, UserContext.get(),
                List.of(new SlotSpec(timeOfDay.name(), customTime)),
                startDate, endDate);
    }
}
