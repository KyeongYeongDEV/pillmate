package com.pillmate.caregroup.application.dto;

import java.time.LocalDate;
import java.util.List;

public record GroupMonthScheduleResponse(
        String month,
        List<GroupDayView> days
) {
    public record GroupDayView(
            LocalDate date,
            List<MemberAdherenceView> members
    ) {}

    public record MemberAdherenceView(
            Long userId,
            String adherence
    ) {}
}
