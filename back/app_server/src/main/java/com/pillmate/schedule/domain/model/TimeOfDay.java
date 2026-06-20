package com.pillmate.schedule.domain.model;

import java.time.LocalTime;

public enum TimeOfDay {
    MORNING(LocalTime.of(8, 0)),
    NOON(LocalTime.of(12, 30)),
    EVENING(LocalTime.of(19, 0)),
    BEDTIME(LocalTime.of(22, 0));

    private final LocalTime defaultTime;

    TimeOfDay(LocalTime defaultTime) {
        this.defaultTime = defaultTime;
    }

    public LocalTime defaultTime() {
        return defaultTime;
    }
}
