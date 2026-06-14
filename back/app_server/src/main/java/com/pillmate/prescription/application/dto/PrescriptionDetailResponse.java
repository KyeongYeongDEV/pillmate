package com.pillmate.prescription.application.dto;

import com.pillmate.prescription.domain.model.OcrStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PrescriptionDetailResponse(
        Long id,
        LocalDate prescribedAt,
        OcrStatus ocrStatus,
        String imageUrl,
        List<DrugDetail> drugs
) {
    public record DrugDetail(
            String nameRaw,
            String matchedDrugName,
            BigDecimal doseAmount,
            String doseUnit,
            Integer frequency,
            Integer durationDays,
            BigDecimal confidence
    ) {}
}
