package com.pillmate.report.domain.model;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "health_reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HealthReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "care_group_id", nullable = false)
    private Long careGroupId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", nullable = false, length = 10)
    private PeriodType periodType;

    @Column(name = "overall_score", nullable = false)
    private Integer overallScore;

    @Column(name = "score_delta")
    private Integer scoreDelta;

    @Column(name = "adherence_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal adherenceRate;

    @Column(name = "total_doses", nullable = false)
    private Integer totalDoses;

    @Column(name = "taken_doses", nullable = false)
    private Integer takenDoses;

    @Column(name = "skipped_doses", nullable = false)
    private Integer skippedDoses;

    @Column(name = "delayed_doses", nullable = false)
    private Integer delayedDoses;

    @Type(JsonBinaryType.class)
    @Column(name = "daily_breakdown", nullable = false, columnDefinition = "jsonb")
    private List<DailyBreakdown> dailyBreakdown = new ArrayList<>();

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReportInsight> insights = new ArrayList<>();

    public static HealthReport create(Long careGroupId, Long patientId,
                                       PeriodType periodType,
                                       LocalDate periodStart, LocalDate periodEnd,
                                       int overallScore, Integer scoreDelta,
                                       BigDecimal adherenceRate,
                                       int totalDoses, int takenDoses,
                                       int skippedDoses, int delayedDoses,
                                       List<DailyBreakdown> breakdown) {
        HealthReport r = new HealthReport();
        r.careGroupId = careGroupId;
        r.patientId = patientId;
        r.periodType = periodType;
        r.periodStart = periodStart;
        r.periodEnd = periodEnd;
        r.overallScore = overallScore;
        r.scoreDelta = scoreDelta;
        r.adherenceRate = adherenceRate;
        r.totalDoses = totalDoses;
        r.takenDoses = takenDoses;
        r.skippedDoses = skippedDoses;
        r.delayedDoses = delayedDoses;
        r.dailyBreakdown = new ArrayList<>(breakdown);
        r.generatedAt = Instant.now();
        return r;
    }

    public List<DailyBreakdown> getDailyBreakdown() {
        return Collections.unmodifiableList(dailyBreakdown);
    }

    public List<ReportInsight> getInsights() {
        return Collections.unmodifiableList(insights);
    }

    public void addInsight(ReportInsight insight) {
        insight.assignTo(this);
        insights.add(insight);
    }

    public void replaceInsights(List<ReportInsight> newInsights) {
        insights.clear();
        newInsights.forEach(this::addInsight);
    }
}
