package com.pillmate.schedule.domain.model;

import java.time.LocalDate;

public enum Adherence {
    FULL, PARTIAL, MISS, UPCOMING;

    public static Adherence of(int takenCount, int totalCount, LocalDate date, LocalDate today) {
        if (isUpcoming(takenCount, date, today)) {
            return UPCOMING;
        }
        return of(takenCount, totalCount);
    }

    public static Adherence of(int takenCount, int totalCount) {
        if (takenCount == 0) {
            return MISS;
        }
        if (takenCount >= totalCount) {
            return FULL;
        }
        return PARTIAL;
    }

    private static boolean isUpcoming(int takenCount, LocalDate date, LocalDate today) {
        return takenCount == 0 && date.isAfter(today);
    }
}
