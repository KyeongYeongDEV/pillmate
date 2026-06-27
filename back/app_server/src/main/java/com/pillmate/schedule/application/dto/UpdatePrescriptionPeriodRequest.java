package com.pillmate.schedule.application.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record UpdatePrescriptionPeriodRequest(
        @NotNull LocalDate endDate
) {}
