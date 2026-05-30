package com.pillmate.schedule.application.dto;

import java.time.LocalDate;
import java.util.List;

public record DayScheduleResponse(
        LocalDate date,
        int totalCount,
        int doneCount,
        List<SlotView> slots
) {}
