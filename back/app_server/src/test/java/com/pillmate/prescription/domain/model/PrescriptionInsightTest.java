package com.pillmate.prescription.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PrescriptionInsight Aggregate — AI 추천 인사이트")
class PrescriptionInsightTest {

    @Test
    @DisplayName("create — confidence >= 0.7 이면 필드 영속화")
    void create_validConfidence_persistsFields() {
        PrescriptionInsight insight = PrescriptionInsight.create(
                42L, PrescriptionInsightType.RECOMMENDATION, PrescriptionInsightSeverity.INFO,
                "비타민 B12 영향 가능", "장기 복용 시 흡수에 영향을 줄 수 있어요.",
                "식약처", new BigDecimal("0.90"));

        assertThat(insight.getPrescriptionId()).isEqualTo(42L);
        assertThat(insight.getType()).isEqualTo(PrescriptionInsightType.RECOMMENDATION);
        assertThat(insight.getSeverity()).isEqualTo(PrescriptionInsightSeverity.INFO);
        assertThat(insight.getTitle()).isEqualTo("비타민 B12 영향 가능");
        assertThat(insight.getSource()).isEqualTo("식약처");
        assertThat(insight.getConfidence()).isEqualByComparingTo("0.90");
        assertThat(insight.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("create — confidence == 0.7 경계값 허용")
    void create_confidenceAtThreshold_allowed() {
        PrescriptionInsight insight = PrescriptionInsight.create(
                1L, PrescriptionInsightType.WARNING, PrescriptionInsightSeverity.WARN,
                "제목", "설명", "식약처", new BigDecimal("0.700"));

        assertThat(insight.getConfidence()).isEqualByComparingTo("0.700");
    }

    @Test
    @DisplayName("create — confidence < 0.7 이면 IllegalArgumentException")
    void create_belowMinConfidence_throws() {
        assertThatThrownBy(() -> PrescriptionInsight.create(
                1L, PrescriptionInsightType.WARNING, PrescriptionInsightSeverity.WARN,
                "제목", "설명", "식약처", new BigDecimal("0.69")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("confidence");
    }

    @Test
    @DisplayName("create — confidence null 이면 IllegalArgumentException")
    void create_nullConfidence_throws() {
        assertThatThrownBy(() -> PrescriptionInsight.create(
                1L, PrescriptionInsightType.WARNING, PrescriptionInsightSeverity.WARN,
                "제목", "설명", "식약처", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("create — source 가 비어있으면 IllegalArgumentException (의료 안전: 출처 강제)")
    void create_blankSource_throws() {
        assertThatThrownBy(() -> PrescriptionInsight.create(
                1L, PrescriptionInsightType.RECOMMENDATION, PrescriptionInsightSeverity.INFO,
                "제목", "설명", "  ", new BigDecimal("0.95")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("source");
    }

    @Test
    @DisplayName("create — source 가 null 이면 IllegalArgumentException")
    void create_nullSource_throws() {
        assertThatThrownBy(() -> PrescriptionInsight.create(
                1L, PrescriptionInsightType.RECOMMENDATION, PrescriptionInsightSeverity.INFO,
                "제목", "설명", null, new BigDecimal("0.95")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
