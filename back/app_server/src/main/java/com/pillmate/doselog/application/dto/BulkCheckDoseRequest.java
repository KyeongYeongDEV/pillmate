package com.pillmate.doselog.application.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record BulkCheckDoseRequest(
        @NotEmpty List<Long> doseLogIds,
        @NotNull String action,   // "TAKE" | "SKIP" | "CANCEL"
        String skipReason
) {}
