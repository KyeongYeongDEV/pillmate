package com.pillmate.schedule.application.dto;

import com.pillmate.schedule.domain.model.TimeOfDay;

import java.time.LocalDate;

public record UpdateScheduleRequest(TimeOfDay timeOfDay, LocalDate endDate) {}
