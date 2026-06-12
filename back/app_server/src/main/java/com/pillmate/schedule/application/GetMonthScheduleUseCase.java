package com.pillmate.schedule.application;

import com.pillmate.schedule.application.dto.MonthScheduleResponse;

import java.time.YearMonth;

public interface GetMonthScheduleUseCase {
    MonthScheduleResponse execute(YearMonth month);
}
