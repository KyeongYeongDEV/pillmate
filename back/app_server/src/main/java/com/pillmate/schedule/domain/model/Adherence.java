package com.pillmate.schedule.domain.model;

public enum Adherence {
    FULL, PARTIAL, MISS;

    public static Adherence of(int takenCount, int totalCount) {
        if (takenCount == 0) {
            return MISS;
        }
        if (takenCount >= totalCount) {
            return FULL;
        }
        return PARTIAL;
    }
}
