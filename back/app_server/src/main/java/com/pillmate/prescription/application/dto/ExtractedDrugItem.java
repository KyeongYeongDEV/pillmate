package com.pillmate.prescription.application.dto;

import java.math.BigDecimal;

public record ExtractedDrugItem(
        String kdCode,
        String nameRaw,
        BigDecimal doseAmount,
        String doseUnit,
        Integer frequency,
        Integer durationDays,
        BigDecimal confidence
) {}
