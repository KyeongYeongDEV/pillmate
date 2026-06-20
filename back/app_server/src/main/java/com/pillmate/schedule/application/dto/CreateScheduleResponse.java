package com.pillmate.schedule.application.dto;

import com.pillmate.schedule.domain.model.Schedule;
import com.pillmate.schedule.domain.model.TimeOfDay;

import java.time.LocalDate;
import java.time.LocalTime;

public record CreateScheduleResponse(
        Long scheduleId,
        Long drugId,
        TimeOfDay timeOfDay,
        LocalTime customTime,
        LocalDate startDate,
        LocalDate endDate
) {
    public static CreateScheduleResponse from(Schedule s) {
        return new CreateScheduleResponse(s.getId(), s.getDrugId(),
                s.getTimeOfDay(), s.getCustomTime(), s.getStartDate(), s.getEndDate());
    }
}
