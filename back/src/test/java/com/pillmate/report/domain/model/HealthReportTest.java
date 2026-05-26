package com.pillmate.report.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HealthReport Aggregate — 인사이트 자식 컬렉션")
class HealthReportTest {

    @Test
    @DisplayName("addInsight 호출 시 자식이 추가되고 부모 양방향 관계가 설정된다")
    void addInsight_appendsAndLinks() {
        HealthReport report = newReport();
        ReportInsight insight = newInsight();

        report.addInsight(insight);

        assertThat(report.getInsights()).hasSize(1);
        assertThat(report.getInsights().get(0).getReport()).isEqualTo(report);
    }

    @Test
    @DisplayName("replaceInsights() 호출 시 기존 인사이트가 새 목록으로 교체된다")
    void replaceInsights_replaces() {
        HealthReport report = newReport();
        report.addInsight(newInsight());
        report.addInsight(newInsight());

        report.replaceInsights(List.of(newInsight()));

        assertThat(report.getInsights()).hasSize(1);
    }

    private HealthReport newReport() {
        return HealthReport.create(1L, 2L, PeriodType.WEEKLY,
                LocalDate.of(2026, 5, 18), LocalDate.of(2026, 5, 24),
                85, 3, new BigDecimal("92.00"),
                30, 28, 2, 1, List.of());
    }

    private ReportInsight newInsight() {
        return ReportInsight.builder()
                .type(InsightType.WARNING)
                .severity(InsightSeverity.WARN)
                .title("저녁약 누락")
                .description("최근 7일 중 3일 저녁약을 빠뜨리셨어요.")
                .source("PillMate AI 분석")
                .build();
    }
}
