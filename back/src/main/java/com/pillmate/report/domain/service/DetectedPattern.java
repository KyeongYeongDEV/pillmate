package com.pillmate.report.domain.service;

public record DetectedPattern(
        PatternType type,
        String label,
        int missedCount,
        int totalCount,
        String drugKdCode
) {
    public enum PatternType {
        EVENING_MISS,
        WEEKDAY_MISS,
        DRUG_MISS,
        TIME_DELAY,
        CONSECUTIVE_MISS
    }

    public static DetectedPattern of(PatternType type, String label, int missed, int total) {
        return new DetectedPattern(type, label, missed, total, null);
    }

    public static DetectedPattern ofDrug(PatternType type, String label, int missed, int total, String kdCode) {
        return new DetectedPattern(type, label, missed, total, kdCode);
    }
}
