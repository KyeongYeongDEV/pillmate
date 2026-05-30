package com.pillmate.schedule.application.port;

import java.time.LocalDate;
import java.util.List;

public interface ScheduleDayQueryPort {

    List<DayScheduleProjection> findByPatientAndDate(Long patientId, LocalDate date);

    record DayScheduleProjection(
            Long scheduleId,
            String timeOfDay,
            String drugName,
            String pillColor,
            Long doseLogId,
            String doseStatus
    ) {}
}
