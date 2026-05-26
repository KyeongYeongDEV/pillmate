package com.pillmate.caregroup.application.dto;

import com.pillmate.caregroup.domain.model.InviteCode;

import java.time.Instant;

public record InviteCodeResponse(String code, Instant expiresAt) {
    public static InviteCodeResponse from(InviteCode code) {
        return new InviteCodeResponse(code.getCode(), code.getExpiresAt());
    }
}
