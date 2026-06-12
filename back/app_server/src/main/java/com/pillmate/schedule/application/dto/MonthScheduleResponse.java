package com.pillmate.schedule.application.dto;

import java.time.LocalDate;
import java.util.List;

public record MonthScheduleResponse(
        String month,
        List<DayAdherenceView> days
) {
    public record DayAdherenceView(
            LocalDate date,
            int totalCount,
            int takenCount,
            String adherence
    ) {}
}
