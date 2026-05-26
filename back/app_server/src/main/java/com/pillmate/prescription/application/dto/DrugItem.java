package com.pillmate.prescription.application.dto;

import java.math.BigDecimal;

public record DrugItem(
        String kdCode,
        String nameRaw,
        BigDecimal doseAmount,
        String doseUnit,
        Integer frequency,
        Integer durationDays,
        BigDecimal confidence,
        String decision,
        String candidateOptionsJson
) {
    public DrugItem(String kdCode, String nameRaw, BigDecimal doseAmount, String doseUnit,
                    Integer frequency, Integer durationDays, BigDecimal confidence) {
        this(kdCode, nameRaw, doseAmount, doseUnit, frequency, durationDays, confidence, "AUTO", null);
    }
}
