package com.pillmate.schedule.domain.model;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Schedule 도메인 — active/update/deactivate/customTime")
class ScheduleTest {

    private static final LocalDate START = LocalDate.of(2026, 5, 25);
    private static final LocalDate END = LocalDate.of(2026, 6, 1);

    private Schedule newSchedule() {
        return Schedule.of(1L, 2L, 10L, TimeOfDay.MORNING, START, END, 1L);
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

    @Test
    @DisplayName("customTime 미지정(null) 생성 시 timeOfDay 기본 시각으로 채운다 — MORNING 08:00")
    void create_whenCustomTimeNull_fillsMorningDefault() {
        Schedule s = Schedule.of(1L, 2L, 10L, TimeOfDay.MORNING, null, START, END, 1L);

        assertThat(s.getCustomTime()).isEqualTo(LocalTime.of(8, 0));
    }

    @Test
    @DisplayName("customTime 미지정(null) 생성 시 timeOfDay 기본 시각으로 채운다 — BEDTIME 22:00")
    void create_whenCustomTimeNull_fillsBedtimeDefault() {
        Schedule s = Schedule.of(1L, 2L, 10L, TimeOfDay.BEDTIME, null, START, END, 1L);

        assertThat(s.getCustomTime()).isEqualTo(LocalTime.of(22, 0));
    }

    @Test
    @DisplayName("customTime 명시 생성 시 해당 시각을 사용한다")
    void create_whenCustomTimeGiven_usesIt() {
        Schedule s = Schedule.of(1L, 2L, 10L, TimeOfDay.NOON, LocalTime.of(13, 15), START, END, 1L);

        assertThat(s.getCustomTime()).isEqualTo(LocalTime.of(13, 15));
    }

    @Test
    @DisplayName("기존 7-인자 of() 는 timeOfDay 기본 시각으로 customTime 을 채운다 — NOON 12:30")
    void create_legacyFactory_fillsNoonDefault() {
        Schedule s = Schedule.of(1L, 2L, 10L, TimeOfDay.NOON, START, END, 1L);

        assertThat(s.getCustomTime()).isEqualTo(LocalTime.of(12, 30));
    }

    @Test
    @DisplayName("복약 기간 내 changeTime 호출 시 customTime 이 변경된다")
    void changeTime_withinPeriod_changesCustomTime() {
        Schedule s = newSchedule();

        s.changeTime(LocalTime.of(9, 30), LocalDate.of(2026, 5, 28));

        assertThat(s.getCustomTime()).isEqualTo(LocalTime.of(9, 30));
    }

    @Test
    @DisplayName("복약 기간 종료일(endDate 당일)에는 changeTime 가능 — 경계 포함")
    void changeTime_onEndDate_succeeds() {
        Schedule s = newSchedule();

        s.changeTime(LocalTime.of(9, 30), END);

        assertThat(s.getCustomTime()).isEqualTo(LocalTime.of(9, 30));
    }

    @Test
    @DisplayName("복약 기간 종료 후 changeTime 호출 시 SCHEDULE_PERIOD_ENDED 예외 — 편집 불가")
    void changeTime_afterEndDate_throws() {
        Schedule s = newSchedule();
        LocalDate afterEnd = END.plusDays(1);

        assertThatThrownBy(() -> s.changeTime(LocalTime.of(9, 30), afterEnd))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SCHEDULE_PERIOD_ENDED);
    }

    @Test
    @DisplayName("기간 종료 후 changeTime 실패 시 기존 customTime 은 보존된다")
    void changeTime_afterEndDate_preservesCustomTime() {
        Schedule s = Schedule.of(1L, 2L, 10L, TimeOfDay.MORNING, LocalTime.of(8, 0), START, END, 1L);
        LocalDate afterEnd = END.plusDays(1);

        assertThatThrownBy(() -> s.changeTime(LocalTime.of(9, 30), afterEnd))
                .isInstanceOf(PillmateException.class);
        assertThat(s.getCustomTime()).isEqualTo(LocalTime.of(8, 0));
    }
}
