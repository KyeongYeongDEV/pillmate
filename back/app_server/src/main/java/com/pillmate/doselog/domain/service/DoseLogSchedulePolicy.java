package com.pillmate.doselog.domain.service;

import com.pillmate.schedule.domain.model.TimeOfDay;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

@Component
public class DoseLogSchedulePolicy {

    static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static final LocalTime MORNING_TIME = LocalTime.of(8, 0);
    private static final LocalTime NOON_TIME    = LocalTime.of(12, 30);
    private static final LocalTime EVENING_TIME = LocalTime.of(19, 0);
    private static final LocalTime BEDTIME_TIME = LocalTime.of(22, 0);

    public Instant scheduledAtFor(TimeOfDay timeOfDay, LocalDate date) {
        LocalTime time = switch (timeOfDay) {
            case MORNING -> MORNING_TIME;
            case NOON    -> NOON_TIME;
            case EVENING -> EVENING_TIME;
            case BEDTIME -> BEDTIME_TIME;
        };
        return date.atTime(time).atZone(KST).toInstant();
    }
}
