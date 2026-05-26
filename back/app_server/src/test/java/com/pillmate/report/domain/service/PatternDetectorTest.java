package com.pillmate.report.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PatternDetector — rule-based 패턴 추출")
class PatternDetectorTest {

    private final PatternDetector sut = new PatternDetector();

    @Test
    @DisplayName("저녁 누락률 20% 초과 → EVENING_MISS 패턴")
    void detect_eveningMissPattern_when20PercentMissed() {
        PatternDetector.PatternInput input = new PatternDetector.PatternInput(
                30, 7, Map.of(), 0, 0);

        var patterns = sut.detect(input);

        assertThat(patterns).extracting(DetectedPattern::type)
                .contains(DetectedPattern.PatternType.EVENING_MISS);
    }

    @Test
    @DisplayName("3일 이상 연속 누락 → CONSECUTIVE_MISS")
    void detect_consecutiveMissPattern_when3DaysInRow() {
        PatternDetector.PatternInput input = new PatternDetector.PatternInput(
                0, 0, Map.of(), 3, 0);

        var patterns = sut.detect(input);

        assertThat(patterns).extracting(DetectedPattern::type)
                .contains(DetectedPattern.PatternType.CONSECUTIVE_MISS);
    }

    @Test
    @DisplayName("특정 drug 누락률 30% 초과 → DRUG_MISS (kdCode 포함)")
    void detect_drugMissPattern_overThreshold() {
        PatternDetector.PatternInput input = new PatternDetector.PatternInput(
                0, 0, Map.of("KD-200", new int[]{5, 10}), 0, 0);

        var patterns = sut.detect(input);

        assertThat(patterns)
                .filteredOn(p -> p.type() == DetectedPattern.PatternType.DRUG_MISS)
                .extracting(DetectedPattern::drugKdCode)
                .containsExactly("KD-200");
    }

    @Test
    @DisplayName("평균 60분 지연 → TIME_DELAY")
    void detect_timeDelayPattern() {
        PatternDetector.PatternInput input = new PatternDetector.PatternInput(
                0, 0, Map.of(), 0, 75);

        var patterns = sut.detect(input);

        assertThat(patterns).extracting(DetectedPattern::type)
                .contains(DetectedPattern.PatternType.TIME_DELAY);
    }
}
