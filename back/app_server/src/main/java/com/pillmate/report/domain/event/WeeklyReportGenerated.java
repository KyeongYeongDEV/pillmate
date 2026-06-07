package com.pillmate.report.domain.event;

import java.time.LocalDate;

public record WeeklyReportGenerated(
        Long actorUserId,
        Long reportId,
        LocalDate weekStart
) {}
