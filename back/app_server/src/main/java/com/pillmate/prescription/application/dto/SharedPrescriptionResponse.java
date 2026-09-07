package com.pillmate.prescription.application.dto;

import com.pillmate.prescription.domain.model.PrescriptionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 케어그룹 내 알약 정보 공유 상세 — 처방전 원본 사진·메모·증상·AI 인사이트·OCR 신뢰도는 의도적으로 미포함.
 */
public record SharedPrescriptionResponse(
        Long id,
        Long ownerUserId,
        LocalDate prescribedAt,
        String label,
        PrescriptionStatus status,
        LocalDate periodStart,
        LocalDate periodEnd,
        Integer daysRemaining,
        Double progressRate,
        Double adherenceRate,
        List<SharedDrugDetail> drugs
) {
    public record SharedDrugDetail(
            String nameRaw,
            String matchedDrugName,
            String matchedKdCode,
            BigDecimal doseAmount,
            String doseUnit,
            Integer frequency,
            Integer durationDays,
            String imageUrl,
            List<NutrientNote> nutrientNotes
    ) {}
}
