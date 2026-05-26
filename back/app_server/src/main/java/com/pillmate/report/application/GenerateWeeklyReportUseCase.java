package com.pillmate.report.application;

import com.pillmate.report.application.port.DoseLogStatsPort;
import com.pillmate.report.application.port.DoseLogStatsPort.DailyCount;
import com.pillmate.report.application.port.DoseLogStatsPort.PeriodStats;
import com.pillmate.report.application.port.LlmInsightPort;
import com.pillmate.report.application.port.PrescriptionContextPort;
import com.pillmate.report.application.port.PrescriptionContextPort.PatientContext;
import com.pillmate.report.domain.model.DailyBreakdown;
import com.pillmate.report.domain.model.HealthReport;
import com.pillmate.report.domain.model.PeriodType;
import com.pillmate.report.domain.model.ReportInsight;
import com.pillmate.report.domain.repository.HealthReportRepository;
import com.pillmate.report.domain.service.DetectedPattern;
import com.pillmate.report.domain.service.PatternDetector;
import com.pillmate.report.domain.service.ScoreCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GenerateWeeklyReportUseCase {

    private final HealthReportRepository reportRepository;
    private final DoseLogStatsPort statsPort;
    private final PrescriptionContextPort prescriptionContextPort;
    private final LlmInsightPort llmInsightPort;
    private final ScoreCalculator scoreCalculator;
    private final PatternDetector patternDetector;

    @Transactional
    public HealthReport generate(Long patientId, LocalDate weekStart) {
        LocalDate weekEnd = weekStart.plusDays(6);
        PatientContext ctx = prescriptionContextPort.loadContext(patientId);
        PeriodStats stats = statsPort.aggregate(patientId, weekStart, weekEnd);
        List<DailyCount> daily = statsPort.dailyCounts(patientId, weekStart, weekEnd);

        int score = scoreCalculator.calculate(
                stats.takenDoses(), stats.totalDoses(), stats.onTimeDoses(), 100);
        BigDecimal adherence = adherenceRate(stats);
        List<DailyBreakdown> breakdown = toBreakdown(daily);

        HealthReport report = HealthReport.create(
                ctx.careGroupId(), patientId, PeriodType.WEEKLY,
                weekStart, weekEnd, score, null, adherence,
                stats.totalDoses(), stats.takenDoses(),
                stats.skippedDoses(), stats.delayedDoses(),
                breakdown);

        addInsights(report, stats, ctx, score, adherence, weekStart, weekEnd);
        return reportRepository.save(report);
    }

    private BigDecimal adherenceRate(PeriodStats stats) {
        if (stats.totalDoses() == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(stats.takenDoses() * 100.0 / stats.totalDoses())
                .setScale(2, RoundingMode.HALF_UP);
    }

    private List<DailyBreakdown> toBreakdown(List<DailyCount> daily) {
        return daily.stream()
                .map(d -> DailyBreakdown.of(d.date(), d.taken(), d.total()))
                .toList();
    }

    private void addInsights(HealthReport report, PeriodStats stats,
                             PatientContext ctx, int score, BigDecimal adherence,
                             LocalDate from, LocalDate to) {
        List<DetectedPattern> patterns = patternDetector.detect(toPatternInput(stats));
        List<LlmInsightPort.InsightDraft> drafts = safeGenerate(
                new LlmInsightPort.InsightContext(report.getPatientId(),
                        PeriodType.WEEKLY, from, to, score, adherence,
                        patterns, ctx.drugs()));
        drafts.forEach(d -> report.addInsight(toEntity(d)));
    }

    private PatternDetector.PatternInput toPatternInput(PeriodStats stats) {
        return new PatternDetector.PatternInput(
                stats.eveningTotal(), stats.eveningMissed(),
                stats.missedByDrug(), stats.maxConsecutiveMissDays(),
                stats.avgDelayMinutes());
    }

    private List<LlmInsightPort.InsightDraft> safeGenerate(LlmInsightPort.InsightContext context) {
        try {
            return Optional.ofNullable(llmInsightPort.generate(context)).orElse(List.of());
        } catch (RuntimeException ex) {
            log.warn("LLM insight generation failed patientId={} reason={}",
                    context.patientId(), ex.getClass().getSimpleName());
            return List.of();
        }
    }

    private ReportInsight toEntity(LlmInsightPort.InsightDraft d) {
        return ReportInsight.builder()
                .type(d.type())
                .severity(d.severity())
                .title(d.title())
                .description(d.description())
                .source(d.source())
                .build();
    }
}
