package com.pillmate.report.application.port;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface DoseLogStatsPort {

    PeriodStats aggregate(Long patientId, LocalDate from, LocalDate to);

    List<DailyCount> dailyCounts(Long patientId, LocalDate from, LocalDate to);

    record PeriodStats(
            int totalDoses,
            int takenDoses,
            int skippedDoses,
            int delayedDoses,
            int onTimeDoses,
            int eveningTotal,
            int eveningMissed,
            int maxConsecutiveMissDays,
            int avgDelayMinutes,
            Map<String, int[]> missedByDrug
    ) {}

    record DailyCount(LocalDate date, int taken, int total) {}
}
