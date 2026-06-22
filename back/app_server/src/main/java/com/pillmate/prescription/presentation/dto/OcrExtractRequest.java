package com.pillmate.prescription.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record OcrExtractRequest(
        LocalDate prescribedAt,
        @NotBlank String imageKey
) {}
