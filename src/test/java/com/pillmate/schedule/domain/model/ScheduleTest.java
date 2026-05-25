package com.pillmate.schedule.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Schedule 도메인 — active/update/deactivate")
class ScheduleTest {

    private Schedule newSchedule() {
        return Schedule.of(1L, 2L, 10L, TimeOfDay.MORNING,
                LocalDate.of(2026, 5, 25), LocalDate.of(2026, 6, 1), 1L);
    }

    @Test
    @DisplayName("생성 직후 active true")
    void create_assignsActiveTrue() {
        Schedule s = newSchedule();

        assertThat(s.isActive()).isTrue();
    }

    @Test
    @DisplayName("deactivate() 호출 시 active false")
    void deactivate_setsActiveFalse() {
        Schedule s = newSchedule();

        s.deactivate();

        assertThat(s.isActive()).isFalse();
    }

    @Test
    @DisplayName("updateTimeOfDay() 호출 시 timeOfDay 가 변경된다")
    void updateTimeOfDay_changesTimeOfDay() {
        Schedule s = newSchedule();

        s.updateTimeOfDay(TimeOfDay.EVENING);

        assertThat(s.getTimeOfDay()).isEqualTo(TimeOfDay.EVENING);
    }

    @Test
    @DisplayName("updateEndDate() 호출 시 endDate 가 변경된다")
    void updateEndDate_changesEndDate() {
        Schedule s = newSchedule();
        LocalDate newEnd = LocalDate.of(2026, 6, 15);

        s.updateEndDate(newEnd);

        assertThat(s.getEndDate()).isEqualTo(newEnd);
    }
}
