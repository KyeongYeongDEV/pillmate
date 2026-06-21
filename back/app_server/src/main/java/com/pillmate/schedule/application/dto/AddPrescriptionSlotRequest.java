package com.pillmate.schedule.application.dto;

import com.pillmate.schedule.domain.model.TimeOfDay;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public record AddPrescriptionSlotRequest(
        @NotNull TimeOfDay timeOfDay,
        LocalTime customTime
) {}
