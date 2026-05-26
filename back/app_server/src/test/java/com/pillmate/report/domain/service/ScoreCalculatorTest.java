package com.pillmate.report.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ScoreCalculator — 복약 0.7 + 시간 0.2 + 완수 0.1")
class ScoreCalculatorTest {

    private final ScoreCalculator sut = new ScoreCalculator();

    @Test
    @DisplayName("모두 정시 복용 + 처방 완수 100% → 100점")
    void calculate_whenAllTaken_returns100() {
        int score = sut.calculate(30, 30, 30, 100);
        assertThat(score).isEqualTo(100);
    }

    @Test
    @DisplayName("복약 100% + 시간 50% + 완수 100% → 90점")
    void calculate_whenPartialDelay_appliesWeights() {
        int score = sut.calculate(30, 30, 15, 100);
        assertThat(score).isEqualTo(90);
    }

    @Test
    @DisplayName("total 0 이면 0점")
    void calculate_whenNoDoses_returnsZero() {
        assertThat(sut.calculate(0, 0, 0, 100)).isZero();
    }
}
