package com.pillmate.prescription.presentation.dto;

import jakarta.validation.constraints.NotNull;

public record ResolveCandidateRequest(
        @NotNull Long selectedDrugId
) {}
