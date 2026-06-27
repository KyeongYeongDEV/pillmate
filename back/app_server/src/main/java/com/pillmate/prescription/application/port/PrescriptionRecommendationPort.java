package com.pillmate.prescription.application.port;

import com.pillmate.prescription.domain.model.PrescriptionInsightSeverity;
import com.pillmate.prescription.domain.model.PrescriptionInsightType;

import java.math.BigDecimal;
import java.util.List;

public interface PrescriptionRecommendationPort {

    List<InsightDraft> generate(Long prescriptionId, Long patientId, List<DrugContext> drugs);

    record DrugContext(
            String code,
            String name,
            BigDecimal doseAmount,
            String doseUnit,
            Integer frequency,
            Integer durationDays
    ) {}

    record InsightDraft(
            PrescriptionInsightType type,
            PrescriptionInsightSeverity severity,
            String title,
            String description,
            String source,
            BigDecimal confidence
    ) {}
}
