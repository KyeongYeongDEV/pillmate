package com.pillmate.caregroup.application.dto;

import com.pillmate.caregroup.domain.model.CareGroup;
import com.pillmate.caregroup.domain.model.MemberRole;

public record CreateGroupResponse(Long groupId, String name, String role, String inviteCode) {
    public static CreateGroupResponse of(CareGroup group, String inviteCode) {
        return new CreateGroupResponse(group.getId(), group.getName(), MemberRole.ADMIN.name(), inviteCode);
    }
}
