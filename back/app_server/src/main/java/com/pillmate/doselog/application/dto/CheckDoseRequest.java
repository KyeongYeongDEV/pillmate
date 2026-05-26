package com.pillmate.doselog.application.dto;

import jakarta.validation.constraints.NotNull;

public record CheckDoseRequest(
        @NotNull Long doseLogId,
        @NotNull String action,  // "TAKE" or "SKIP"
        String skipReason
) {}
