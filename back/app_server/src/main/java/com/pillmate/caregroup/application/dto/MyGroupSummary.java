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
    // color 는 사용자가 직접 고른 고정 팔레트 색(설정 안 했으면 null → 프론트가 자동배정 팔레트로 폴백).
    public record MemberPreview(Long userId, String name, String color) {}

    public record LastActivitySummary(
            String summary,
            String activityType,
            String severity,
            Instant occurredAt) {}
}
