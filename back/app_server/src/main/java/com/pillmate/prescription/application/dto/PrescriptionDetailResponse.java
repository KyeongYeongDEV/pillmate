package com.pillmate.prescription.application.dto;

import com.pillmate.prescription.domain.model.OcrStatus;
import com.pillmate.prescription.domain.model.PrescriptionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PrescriptionDetailResponse(
        Long id,
        LocalDate prescribedAt,
        OcrStatus ocrStatus,
        String imageUrl,
        List<DrugDetail> drugs,
        String label,
        String memo,
        String symptom,
        PrescriptionStatus status,
        LocalDate periodStart,
        LocalDate periodEnd,
        Integer daysRemaining,
        Double progressRate,
        Double adherenceRate,
        List<PrescriptionInsightView> insights
) {
    public record DrugDetail(
            String nameRaw,
            String matchedDrugName,
            String matchedKdCode,
            BigDecimal doseAmount,
            String doseUnit,
            Integer frequency,
            Integer durationDays,
            BigDecimal confidence,
            String imageUrl,
            List<NutrientNote> nutrientNotes
    ) {}
}
