package com.pillmate.schedule.application.dto;

import com.pillmate.schedule.domain.model.Schedule;
import com.pillmate.schedule.domain.model.TimeOfDay;

import java.time.LocalDate;
import java.time.LocalTime;

public record ScheduleResponse(
        Long scheduleId,
        Long careGroupId,
        Long patientId,
        Long drugId,
        TimeOfDay timeOfDay,
        LocalTime customTime,
        LocalDate startDate,
        LocalDate endDate,
        boolean active
) {
    public static ScheduleResponse from(Schedule s) {
        return new ScheduleResponse(s.getId(), s.getCareGroupId(), s.getPatientId(),
                s.getDrugId(), s.getTimeOfDay(), s.getCustomTime(),
                s.getStartDate(), s.getEndDate(), s.isActive());
    }
}
