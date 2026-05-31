package com.pillmate.caregroup.application.dto;

import java.time.Instant;

public record ActivityView(
        String actorName,
        String activityType,
        String summary,
        Instant occurredAt
) {}
