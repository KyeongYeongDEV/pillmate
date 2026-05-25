package com.pillmate.drug.application.dto;

import java.math.BigDecimal;

public record DrugMasterCandidate(
        String itemSeq,
        String productName,
        String ingredientName,
        BigDecimal doseAmount,
        String doseUnit,
        String form,
        String company,
        String imageUrl,
        Long legacyDrugId,
        int confidence
) {}
