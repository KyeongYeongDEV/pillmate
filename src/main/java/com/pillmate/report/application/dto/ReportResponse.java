package com.pillmate.report.application.dto;

import com.pillmate.report.domain.model.DailyBreakdown;
import com.pillmate.report.domain.model.HealthReport;
import com.pillmate.report.domain.model.PeriodType;
import com.pillmate.report.domain.model.ReportInsight;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ReportResponse(
        Long reportId,
        Long patientId,
        PeriodType periodType,
        LocalDate periodStart,
        LocalDate periodEnd,
        int overallScore,
        Integer scoreDelta,
        BigDecimal adherenceRate,
        int totalDoses,
        int takenDoses,
        int skippedDoses,
        int delayedDoses,
        List<DailyBreakdown> dailyBreakdown,
        List<InsightView> insights
) {
    public record InsightView(
            String type, String severity, String title, String description, String source) {
        public static InsightView from(ReportInsight i) {
            return new InsightView(i.getType().name(), i.getSeverity().name(),
                    i.getTitle(), i.getDescription(), i.getSource());
        }
    }

    public static ReportResponse from(HealthReport r) {
        return new ReportResponse(
                r.getId(), r.getPatientId(), r.getPeriodType(),
                r.getPeriodStart(), r.getPeriodEnd(),
                r.getOverallScore(), r.getScoreDelta(), r.getAdherenceRate(),
                r.getTotalDoses(), r.getTakenDoses(),
                r.getSkippedDoses(), r.getDelayedDoses(),
                r.getDailyBreakdown(),
                r.getInsights().stream().map(InsightView::from).toList());
    }
}
