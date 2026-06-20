package com.pillmate.schedule.application.dto;

import com.pillmate.schedule.domain.model.TimeOfDay;

import java.time.LocalDate;
import java.time.LocalTime;

public record UpdateScheduleRequest(TimeOfDay timeOfDay, LocalTime customTime, LocalDate endDate) {}
