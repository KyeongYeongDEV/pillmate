package com.pillmate.caregroup.application.dto;

import java.time.Instant;
import java.util.List;

public record MyGroupSummary(
        Long groupId,
        String name,
        String role,
        int memberCount,
        List<String> membersPreview,
        LastActivitySummary lastActivity,
        int unreadCount,
        boolean pinned
) {
    public record LastActivitySummary(
            String summary,
            String activityType,
            String severity,
            Instant occurredAt) {}
}
