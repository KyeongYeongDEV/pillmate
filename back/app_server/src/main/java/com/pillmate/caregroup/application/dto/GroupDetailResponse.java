package com.pillmate.caregroup.application.dto;

import java.util.List;

public record GroupDetailResponse(
        Long groupId,
        String name,
        int memberCount,
        List<MemberView> members,
        InviteCodeView inviteCode,
        List<ActivityView> recentActivities
) {}
