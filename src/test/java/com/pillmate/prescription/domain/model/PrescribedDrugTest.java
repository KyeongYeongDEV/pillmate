package com.pillmate.prescription.domain.model;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PrescribedDrug — 매칭 여부 식별")
class PrescribedDrugTest {

    @Test
    @DisplayName("drugId 가 null 이면 isMatched() 는 false (식약처 자동 매칭 실패)")
    void isMatched_whenDrugIdNull_false() {
        PrescribedDrug drug = unmatched("동광나자티딘캡슐150mg");

        assertThat(drug.isMatched()).isFalse();
    }

    @Test
    @DisplayName("drugId 가 존재하면 isMatched() 는 true")
    void isMatched_whenDrugIdPresent_true() {
        PrescribedDrug drug = matched(101L, "타이레놀");

        assertThat(drug.isMatched()).isTrue();
    }

    private PrescribedDrug unmatched(String nameRaw) {
        return PrescribedDrug.builder()
                .drugId(null)
                .nameRaw(nameRaw)
                .doseAmount(new BigDecimal("150.00"))
                .doseUnit("mg")
                .frequency(2)
                .durationDays(7)
                .confidence(new BigDecimal("0.95"))
                .build();
    }

    private PrescribedDrug matched(Long drugId, String nameRaw) {
        return PrescribedDrug.builder()
                .drugId(drugId)
                .nameRaw(nameRaw)
                .doseAmount(new BigDecimal("1.00"))
                .doseUnit("정")
                .frequency(3)
                .durationDays(7)
                .confidence(new BigDecimal("0.95"))
                .build();
    }
}
