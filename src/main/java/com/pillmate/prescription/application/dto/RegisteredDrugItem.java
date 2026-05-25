package com.pillmate.prescription.application.dto;

import java.math.BigDecimal;

public record RegisteredDrugItem(
        Long drugId,
        String kdCode,
        String nameRaw,
        String matchedName,
        BigDecimal confidence,
        String imageUrl
) {}
