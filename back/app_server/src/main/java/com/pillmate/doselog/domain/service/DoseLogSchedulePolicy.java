package com.pillmate.doselog.domain.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

@Component
public class DoseLogSchedulePolicy {

    static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public Instant scheduledAtFor(LocalTime customTime, LocalDate date) {
        return date.atTime(customTime).atZone(KST).toInstant();
    }

    public Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(KST).toInstant();
    }

    public Instant startOfNextDay(LocalDate date) {
        return date.plusDays(1).atStartOfDay(KST).toInstant();
    }
}
