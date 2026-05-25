package com.pillmate.report.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "report_insights")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportInsight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private HealthReport report;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InsightType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private InsightSeverity severity;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 100)
    private String source;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Builder
    private ReportInsight(InsightType type, InsightSeverity severity,
                          String title, String description, String source) {
        this.type = type;
        this.severity = severity;
        this.title = title;
        this.description = description;
        this.source = source;
        this.createdAt = Instant.now();
    }

    void assignTo(HealthReport report) {
        this.report = report;
    }
}
