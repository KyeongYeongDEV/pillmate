package com.pillmate.schedule.application.dto;

import com.pillmate.schedule.domain.model.TimeOfDay;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public record CreateScheduleRequest(
        @NotNull Long careGroupId,
        @NotNull Long patientId,
        @NotNull Long drugId,
        @NotNull TimeOfDay timeOfDay,
        LocalTime customTime,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate
) {}
