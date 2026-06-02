package com.pillmate.user.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record RegisterPushTokenRequest(
        @NotBlank String token,
        String provider
) {}
