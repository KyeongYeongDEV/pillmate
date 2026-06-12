package com.pillmate.schedule.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Adherence — 일 단위 복약 이행 판정 (/schedules/day 와 동일 기준)")
class AdherenceTest {

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
}
