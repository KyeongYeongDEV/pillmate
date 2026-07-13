package com.pillmate.schedule.application.port;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public interface PeriodAdjustDoseLogsPort {

    int createLogsForRange(Long scheduleId, Long patientId, LocalTime customTime,
                           LocalDate fromDate, LocalDate toDate);

    void skipPendingAfter(Long scheduleId, LocalDate cutoffDate);

    void skipPendingFrom(Long scheduleId, Instant fromInclusive);
}
