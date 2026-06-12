package com.pillmate.doselog.domain.service;

import com.pillmate.schedule.domain.model.TimeOfDay;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DoseLogSchedulePolicy — TimeOfDay to KST Instant 변환")
class DoseLogSchedulePolicyTest {

    private final DoseLogSchedulePolicy policy = new DoseLogSchedulePolicy();
    private static final LocalDate JUNE_12 = LocalDate.of(2026, 6, 12);

    @Test
    @DisplayName("MORNING → KST 08:00 (= UTC 전날 23:00, KST +9h)")
    void scheduledAt_morning_returnsKst8am() {
        assertThat(policy.scheduledAtFor(TimeOfDay.MORNING, JUNE_12))
                .isEqualTo(Instant.parse("2026-06-11T23:00:00Z"));
    }

    @Test
    @DisplayName("NOON → KST 12:30 (= UTC 03:30)")
    void scheduledAt_noon_returnsKst1230pm() {
        assertThat(policy.scheduledAtFor(TimeOfDay.NOON, JUNE_12))
                .isEqualTo(Instant.parse("2026-06-12T03:30:00Z"));
    }

    @Test
    @DisplayName("EVENING → KST 19:00 (= UTC 10:00)")
    void scheduledAt_evening_returnsKst7pm() {
        assertThat(policy.scheduledAtFor(TimeOfDay.EVENING, JUNE_12))
                .isEqualTo(Instant.parse("2026-06-12T10:00:00Z"));
    }

    @Test
    @DisplayName("BEDTIME → KST 22:00 (= UTC 13:00)")
    void scheduledAt_bedtime_returnsKst10pm() {
        assertThat(policy.scheduledAtFor(TimeOfDay.BEDTIME, JUNE_12))
                .isEqualTo(Instant.parse("2026-06-12T13:00:00Z"));
    }

    @Test
    @DisplayName("모든 TimeOfDay 값이 null 없이 처리됨 (switch exhaustive)")
    void scheduledAt_allValues_returnNonNull() {
        for (TimeOfDay tod : TimeOfDay.values()) {
            assertThat(policy.scheduledAtFor(tod, JUNE_12)).isNotNull();
        }
    }
}
