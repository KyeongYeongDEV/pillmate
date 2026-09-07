package com.pillmate.caregroup.application.dto;

import com.pillmate.schedule.application.dto.DayScheduleResponse;

import java.time.LocalDate;
import java.util.List;

public record GroupDayScheduleResponse(
        LocalDate date,
        List<MemberDayView> members
) {
    public record MemberDayView(
            Long userId,
            String name,
            DayScheduleResponse schedule
    ) {}
}
