package com.pillmate.schedule.domain;

import com.pillmate.schedule.domain.model.Schedule;
import com.pillmate.schedule.domain.model.TimeOfDay;
import com.pillmate.schedule.domain.service.ScheduleConflictChecker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ScheduleConflictChecker — 복약 스케줄 충돌 검사")
class ScheduleConflictCheckerTest {

    private final ScheduleConflictChecker checker = new ScheduleConflictChecker();

    @Test
    @DisplayName("같은 환자, 같은 시간대, 기간 겹침 → 충돌")
    void conflict_samePatientSameTime() {
        Schedule existing = Schedule.of(1L, 1L, 1L, TimeOfDay.MORNING,
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31), 1L);

        boolean result = checker.hasConflict(1L, TimeOfDay.MORNING,
                LocalDate.of(2026, 5, 15), LocalDate.of(2026, 6, 15),
                List.of(existing));

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("같은 환자, 다른 시간대 → 충돌 아님")
    void noConflict_differentTimeOfDay() {
        Schedule existing = Schedule.of(1L, 1L, 1L, TimeOfDay.MORNING,
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31), 1L);

        boolean result = checker.hasConflict(1L, TimeOfDay.NOON,
                LocalDate.of(2026, 5, 15), LocalDate.of(2026, 6, 15),
                List.of(existing));

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("기간이 전혀 겹치지 않으면 충돌 아님")
    void noConflict_nonOverlappingPeriod() {
        Schedule existing = Schedule.of(1L, 1L, 1L, TimeOfDay.MORNING,
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 10), 1L);

        boolean result = checker.hasConflict(1L, TimeOfDay.MORNING,
                LocalDate.of(2026, 5, 15), LocalDate.of(2026, 6, 15),
                List.of(existing));

        assertThat(result).isFalse();
    }
}
