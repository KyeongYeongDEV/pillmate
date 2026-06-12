package com.pillmate.schedule.application.port;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface ScheduleMonthQueryPort {

    List<DayDoseCount> findDailyDoseCounts(Long patientId, Instant fromInclusive, Instant toExclusive);

    record DayDoseCount(
            LocalDate date,
            int totalCount,
            int takenCount
    ) {}
}
