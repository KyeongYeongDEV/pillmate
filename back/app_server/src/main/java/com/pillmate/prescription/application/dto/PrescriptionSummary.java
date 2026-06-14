package com.pillmate.prescription.application.dto;

import com.pillmate.prescription.domain.model.OcrStatus;

import java.time.Instant;
import java.time.LocalDate;

public record PrescriptionSummary(
        Long id,
        LocalDate prescribedAt,
        OcrStatus ocrStatus,
        int drugCount,
        String drugNames,
        Instant createdAt
) {}
