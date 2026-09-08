package com.pillmate.caregroup.application.dto;

import java.time.Instant;
import java.util.List;

public record MyGroupSummary(
        Long groupId,
        String name,
        String role,
        int memberCount,
        List<MemberPreview> membersPreview,
        LastActivitySummary lastActivity,
        int unreadCount,
        boolean pinned
) {
    // 목록에서도 상세와 동일한 구성원 고유색을 쓰려면 userId 가 필요하다(색 배정 기준이 userId).
    public record MemberPreview(Long userId, String name) {}

    public record LastActivitySummary(
            String summary,
            String activityType,
            String severity,
            Instant occurredAt) {}
}
