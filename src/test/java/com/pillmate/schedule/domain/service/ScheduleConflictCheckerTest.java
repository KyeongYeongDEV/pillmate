package com.pillmate.schedule.domain.service;

import com.pillmate.schedule.domain.model.Schedule;
import com.pillmate.schedule.domain.model.TimeOfDay;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ScheduleConflictChecker — patient/time/drug 활성 충돌")
class ScheduleConflictCheckerTest {

    private final ScheduleConflictChecker checker = new ScheduleConflictChecker();

    @Test
    @DisplayName("같은 patient + 같은 time + 같은 drug 활성 → 충돌")
    void detect_whenSamePatientSameTimeSameDrug_conflict() {
        Schedule existing = active(2L, 10L, TimeOfDay.MORNING);

        boolean conflict = checker.hasConflict(
                2L, 10L, TimeOfDay.MORNING,
                LocalDate.of(2026, 5, 25), LocalDate.of(2026, 6, 1),
                List.of(existing));

        assertThat(conflict).isTrue();
    }

    @Test
    @DisplayName("active=false 스케줄은 충돌 대상 아님")
    void detect_whenInactive_noConflict() {
        Schedule existing = active(2L, 10L, TimeOfDay.MORNING);
        existing.deactivate();

        boolean conflict = checker.hasConflict(
                2L, 10L, TimeOfDay.MORNING,
                LocalDate.of(2026, 5, 25), LocalDate.of(2026, 6, 1),
                List.of(existing));

        assertThat(conflict).isFalse();
    }

    @Test
    @DisplayName("다른 drug 이면 같은 시간대라도 충돌 아님")
    void detect_whenDifferentDrug_noConflict() {
        Schedule existing = active(2L, 11L, TimeOfDay.MORNING);

        boolean conflict = checker.hasConflict(
                2L, 10L, TimeOfDay.MORNING,
                LocalDate.of(2026, 5, 25), LocalDate.of(2026, 6, 1),
                List.of(existing));

        assertThat(conflict).isFalse();
    }

    @Test
    @DisplayName("다른 time_of_day 이면 충돌 아님")
    void detect_whenDifferentTimeOfDay_noConflict() {
        Schedule existing = active(2L, 10L, TimeOfDay.EVENING);

        boolean conflict = checker.hasConflict(
                2L, 10L, TimeOfDay.MORNING,
                LocalDate.of(2026, 5, 25), LocalDate.of(2026, 6, 1),
                List.of(existing));

        assertThat(conflict).isFalse();
    }

    private Schedule active(Long patientId, Long drugId, TimeOfDay time) {
        return Schedule.of(1L, patientId, drugId, time,
                LocalDate.of(2026, 5, 25), LocalDate.of(2026, 6, 30), 1L);
    }
}
