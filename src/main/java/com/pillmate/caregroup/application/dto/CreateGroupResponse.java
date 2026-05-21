package com.pillmate.caregroup.application.dto;

import com.pillmate.caregroup.domain.model.CareGroup;

public record CreateGroupResponse(Long groupId, String name, String inviteCode) {
    public static CreateGroupResponse of(CareGroup group, String inviteCode) {
        return new CreateGroupResponse(group.getId(), group.getName(), inviteCode);
    }
}
