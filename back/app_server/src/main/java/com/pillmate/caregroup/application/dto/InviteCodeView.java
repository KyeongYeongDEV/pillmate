package com.pillmate.caregroup.application.dto;

import java.time.Instant;

public record InviteCodeView(String code, Instant expiresAt) {}
