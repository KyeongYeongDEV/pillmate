package com.pillmate.common.prescription;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PrescriptionLabel — 처방전 이름 포맷")
class PrescriptionLabelTest {

    private static final LocalDate JUN_21 = LocalDate.of(2026, 6, 21);

    @Test
    @DisplayName("약 3종 → '6월21일·대표약 외2종'")
    void of_multipleDrugs_showsLeadAndOthersCount() {
        // when
        String label = PrescriptionLabel.of(JUN_21, "타이레놀", 3);

        // then
        assertThat(label).isEqualTo("6월21일·타이레놀 외2종");
    }

    @Test
    @DisplayName("약 1종 → '6월21일·대표약' (외N종 없음)")
    void of_singleDrug_showsLeadOnly() {
        // when
        String label = PrescriptionLabel.of(JUN_21, "타이레놀", 1);

        // then
        assertThat(label).isEqualTo("6월21일·타이레놀");
    }

    @Test
    @DisplayName("약 0종 → '6월21일 처방전'")
    void of_noDrug_showsDatePrescription() {
        // when
        String label = PrescriptionLabel.of(JUN_21, null, 0);

        // then
        assertThat(label).isEqualTo("6월21일 처방전");
    }

    @Test
    @DisplayName("대표약명이 공백이면 약 0종과 동일하게 fallback")
    void of_blankLeadName_fallsBackToDatePrescription() {
        // when
        String label = PrescriptionLabel.of(JUN_21, "  ", 2);

        // then
        assertThat(label).isEqualTo("6월21일 처방전");
    }
}
