package com.pillmate.prescription.application.dto;

import com.pillmate.prescription.domain.model.PrescriptionInsight;
import com.pillmate.prescription.domain.model.PrescriptionInsightSeverity;
import com.pillmate.prescription.domain.model.PrescriptionInsightType;

import java.math.BigDecimal;

public record PrescriptionInsightView(
        Long id,
        PrescriptionInsightType type,
        PrescriptionInsightSeverity severity,
        String title,
        String description,
        String source,
        BigDecimal confidence
) {
    public static PrescriptionInsightView from(PrescriptionInsight insight) {
        return new PrescriptionInsightView(
                insight.getId(), insight.getType(), insight.getSeverity(),
                insight.getTitle(), insight.getDescription(), insight.getSource(), insight.getConfidence());
    }
}
