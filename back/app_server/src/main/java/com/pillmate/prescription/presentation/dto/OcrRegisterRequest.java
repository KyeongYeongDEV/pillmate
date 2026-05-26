package com.pillmate.prescription.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record OcrRegisterRequest(
        @NotNull Long careGroupId,
        @NotNull Long patientId,
        @NotNull LocalDate prescribedAt,
        @NotBlank String imageKey
) {}
