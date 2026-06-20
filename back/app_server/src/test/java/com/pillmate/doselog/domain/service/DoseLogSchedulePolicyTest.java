package com.pillmate.doselog.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DoseLogSchedulePolicy — customTime to KST Instant 변환")
class DoseLogSchedulePolicyTest {

    private final DoseLogSchedulePolicy policy = new DoseLogSchedulePolicy();
    private static final LocalDate JUNE_12 = LocalDate.of(2026, 6, 12);

    @Test
    @DisplayName("KST 08:00 → UTC 전날 23:00 (KST +9h)")
    void scheduledAt_8am_returnsUtc2300PrevDay() {
        assertThat(policy.scheduledAtFor(LocalTime.of(8, 0), JUNE_12))
                .isEqualTo(Instant.parse("2026-06-11T23:00:00Z"));
    }

    @Test
    @DisplayName("KST 12:30 → UTC 03:30")
    void scheduledAt_1230_returnsUtc0330() {
        assertThat(policy.scheduledAtFor(LocalTime.of(12, 30), JUNE_12))
                .isEqualTo(Instant.parse("2026-06-12T03:30:00Z"));
    }

    @Test
    @DisplayName("KST 19:00 → UTC 10:00")
    void scheduledAt_1900_returnsUtc1000() {
        assertThat(policy.scheduledAtFor(LocalTime.of(19, 0), JUNE_12))
                .isEqualTo(Instant.parse("2026-06-12T10:00:00Z"));
    }

    @Test
    @DisplayName("커스텀 시각 09:15 → UTC 00:15 — 임의 시각 반영")
    void scheduledAt_customMinute_returnsUtc0015() {
        assertThat(policy.scheduledAtFor(LocalTime.of(9, 15), JUNE_12))
                .isEqualTo(Instant.parse("2026-06-12T00:15:00Z"));
    }

    @Test
    @DisplayName("startOfDay — KST 자정 = UTC 전날 15:00 (날짜 멱등 범위 하한)")
    void startOfDay_returnsKstMidnightAsUtc1500PrevDay() {
        assertThat(policy.startOfDay(JUNE_12))
                .isEqualTo(Instant.parse("2026-06-11T15:00:00Z"));
    }

    @Test
    @DisplayName("startOfNextDay — 익일 KST 자정 = UTC 당일 15:00 (멱등 범위 상한, 배타)")
    void startOfNextDay_returnsNextKstMidnightAsUtc1500() {
        assertThat(policy.startOfNextDay(JUNE_12))
                .isEqualTo(Instant.parse("2026-06-12T15:00:00Z"));
    }
}
