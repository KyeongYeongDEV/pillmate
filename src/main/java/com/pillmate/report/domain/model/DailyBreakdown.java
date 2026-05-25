package com.pillmate.report.domain.model;

import java.time.LocalDate;

public record DailyBreakdown(LocalDate date, int taken, int total, String status) {

    public static DailyBreakdown of(LocalDate date, int taken, int total) {
        return new DailyBreakdown(date, taken, total, classify(taken, total));
    }

    private static String classify(int taken, int total) {
        if (total == 0) return "NONE";
        double rate = taken / (double) total;
        if (rate >= 0.9) return "GOOD";
        if (rate >= 0.5) return "PARTIAL";
        return "MISSED";
    }
}
