package com.pillmate.prescription.presentation.dto;

import jakarta.validation.constraints.Size;

public record UpdatePrescriptionMemoRequest(
        @Size(max = 100) String label,
        @Size(max = 500) String memo
) {}
