package com.pillmate.schedule.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Adherence — 일 단위 복약 이행 판정 (/schedules/day 와 동일 기준)")
class AdherenceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);
    private static final LocalDate TOMORROW = TODAY.plusDays(1);

    @Test
    @DisplayName("전부 복용 (taken == total) → FULL")
    void of_whenAllTaken_isFull() {
        assertThat(Adherence.of(4, 4)).isEqualTo(Adherence.FULL);
    }

    @Test
    @DisplayName("일부만 복용 (0 < taken < total) → PARTIAL")
    void of_whenSomeTaken_isPartial() {
        assertThat(Adherence.of(2, 4)).isEqualTo(Adherence.PARTIAL);
    }

    @Test
    @DisplayName("하나도 복용 안 함 (taken == 0) → MISS")
    void of_whenNoneTaken_isMiss() {
        assertThat(Adherence.of(0, 4)).isEqualTo(Adherence.MISS);
    }

    @Test
    @DisplayName("단일 슬롯 복용 완료 (1/1) → FULL")
    void of_whenSingleSlotTaken_isFull() {
        assertThat(Adherence.of(1, 1)).isEqualTo(Adherence.FULL);
    }

    @Test
    @DisplayName("미래 날짜 + 미복용 (taken == 0) → UPCOMING")
    void of_whenFutureDateAndNoneTaken_isUpcoming() {
        assertThat(Adherence.of(0, 4, TOMORROW, TODAY)).isEqualTo(Adherence.UPCOMING);
    }

    @Test
    @DisplayName("미래 날짜 + 선복용 완료 (taken == total) → 기존 규칙대로 FULL (UPCOMING 아님)")
    void of_whenFutureDateButAllTaken_isFull() {
        assertThat(Adherence.of(4, 4, TOMORROW, TODAY)).isEqualTo(Adherence.FULL);
    }

    @Test
    @DisplayName("미래 날짜 + 일부 선복용 (0 < taken < total) → 기존 규칙대로 PARTIAL (UPCOMING 아님)")
    void of_whenFutureDateButPartiallyTaken_isPartial() {
        assertThat(Adherence.of(2, 4, TOMORROW, TODAY)).isEqualTo(Adherence.PARTIAL);
    }

    @Test
    @DisplayName("오늘 날짜 + 미복용 → 기존 규칙대로 MISS (회귀)")
    void of_whenTodayAndNoneTaken_isMiss() {
        assertThat(Adherence.of(0, 4, TODAY, TODAY)).isEqualTo(Adherence.MISS);
    }

    @Test
    @DisplayName("오늘 날짜 + 전체 복용 → 기존 규칙대로 FULL (회귀)")
    void of_whenTodayAndAllTaken_isFull() {
        assertThat(Adherence.of(4, 4, TODAY, TODAY)).isEqualTo(Adherence.FULL);
    }

    @Test
    @DisplayName("과거 날짜 + 미복용 → 기존 규칙대로 MISS (회귀)")
    void of_whenPastDateAndNoneTaken_isMiss() {
        assertThat(Adherence.of(0, 4, YESTERDAY, TODAY)).isEqualTo(Adherence.MISS);
    }

    @Test
    @DisplayName("과거 날짜 + 일부 복용 → 기존 규칙대로 PARTIAL (회귀)")
    void of_whenPastDateAndSomeTaken_isPartial() {
        assertThat(Adherence.of(2, 4, YESTERDAY, TODAY)).isEqualTo(Adherence.PARTIAL);
    }
}
