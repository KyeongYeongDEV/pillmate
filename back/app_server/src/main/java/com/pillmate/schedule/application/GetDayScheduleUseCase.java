package com.pillmate.schedule.application;

import com.pillmate.schedule.application.dto.DayScheduleResponse;

import java.time.LocalDate;

public interface GetDayScheduleUseCase {

    DayScheduleResponse execute(LocalDate date);
}
