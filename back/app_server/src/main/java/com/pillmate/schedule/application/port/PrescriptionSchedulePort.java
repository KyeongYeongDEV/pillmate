package com.pillmate.schedule.application.port;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface PrescriptionSchedulePort {

    List<CreatedSchedule> createForPrescription(CreatePrescriptionSchedulesCommand command);

    record CreatePrescriptionSchedulesCommand(
            Long careGroupId,
            Long patientId,
            Long prescriptionId,
            Long requesterId,
            List<SlotSpec> slots,
            LocalDate startDate,
            LocalDate endDate
    ) {}

    record SlotSpec(String timeOfDay, LocalTime customTime) {}

    record CreatedSchedule(
            Long scheduleId,
            String timeOfDay,
            LocalTime customTime,
            LocalDate startDate,
            LocalDate endDate
    ) {}
}
