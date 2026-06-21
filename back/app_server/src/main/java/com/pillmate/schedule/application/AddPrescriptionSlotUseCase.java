package com.pillmate.schedule.application;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.common.security.UserContext;
import com.pillmate.schedule.application.port.PrescriptionSchedulePort.CreatePrescriptionSchedulesCommand;
import com.pillmate.schedule.application.port.PrescriptionSchedulePort.CreatedSchedule;
import com.pillmate.schedule.application.port.PrescriptionSchedulePort.SlotSpec;
import com.pillmate.schedule.domain.model.Schedule;
import com.pillmate.schedule.domain.model.TimeOfDay;
import com.pillmate.schedule.domain.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AddPrescriptionSlotUseCase {

    private final ScheduleRepository scheduleRepository;
    private final PrescriptionScheduleService prescriptionScheduleService;

    @Transactional
    public List<CreatedSchedule> addSlot(Long prescriptionId, TimeOfDay timeOfDay, LocalTime customTime) {
        List<Schedule> existing = scheduleRepository.findActiveByPrescriptionId(prescriptionId);
        requireExisting(existing);
        requireNoTimeConflict(existing, effectiveTime(timeOfDay, customTime));
        return prescriptionScheduleService.createForPrescription(
                buildCommand(prescriptionId, existing.get(0), timeOfDay, customTime));
    }

    private void requireExisting(List<Schedule> existing) {
        if (existing.isEmpty()) {
            throw new PillmateException(ErrorCode.SCHEDULE_NOT_FOUND);
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
            Long prescriptionId, Schedule sample, TimeOfDay timeOfDay, LocalTime customTime) {
        return new CreatePrescriptionSchedulesCommand(
                sample.getCareGroupId(), sample.getPatientId(), prescriptionId, UserContext.get(),
                List.of(new SlotSpec(timeOfDay.name(), customTime)),
                sample.getStartDate(), sample.getEndDate());
    }
}
