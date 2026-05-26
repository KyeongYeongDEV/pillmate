package com.pillmate.report.application.port;

import com.pillmate.report.domain.model.InsightSeverity;
import com.pillmate.report.domain.model.InsightType;
import com.pillmate.report.domain.model.PeriodType;
import com.pillmate.report.domain.service.DetectedPattern;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface LlmInsightPort {

    List<InsightDraft> generate(InsightContext context);

    record InsightContext(
            Long patientId,
            PeriodType periodType,
            LocalDate periodStart,
            LocalDate periodEnd,
            int score,
            BigDecimal adherenceRate,
            List<DetectedPattern> patterns,
            List<PrescriptionContextPort.DrugSummary> drugs
    ) {}

    record InsightDraft(
            InsightType type,
            InsightSeverity severity,
            String title,
            String description,
            String source
    ) {}
}
