package com.pillmate.prescription.application.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record ScheduleSpec(
        Long careGroupId,
        List<SlotInput> slots,
        LocalDate startDate,
        LocalDate endDate
) {
    public record SlotInput(String timeOfDay, LocalTime customTime) {}
}
