package com.pillmate.prescription.application.port;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface SchedulingPort {

    List<ScheduledSlot> createForPrescription(CreateScheduleCommand command);

    record CreateScheduleCommand(
            Long careGroupId,
            Long patientId,
            Long prescriptionId,
            Long requesterId,
            List<SlotInput> slots,
            LocalDate startDate,
            LocalDate endDate
    ) {}

    record SlotInput(String timeOfDay, LocalTime customTime) {}

    record ScheduledSlot(
            Long scheduleId,
            String timeOfDay,
            LocalTime customTime,
            LocalDate startDate,
            LocalDate endDate
    ) {}
}
