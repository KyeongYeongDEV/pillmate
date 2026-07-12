package com.pillmate.caregroup.domain.event;

public record MemberJoined(
        Long careGroupId,
        Long actorUserId
) {}
