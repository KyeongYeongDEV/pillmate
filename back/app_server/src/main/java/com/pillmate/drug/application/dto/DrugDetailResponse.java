package com.pillmate.drug.application.dto;

import com.pillmate.drug.domain.model.Drug;

public record DrugDetailResponse(
        Long id,
        String kdCode,
        String name,
        String ingredient,
        String efficacy,
        String dosage,
        String sideEffect,
        String form,
        String company,
        String source,
        String imageUrl
) {
    public static DrugDetailResponse from(Drug drug, String imageUrl) {
        return new DrugDetailResponse(
                drug.getId(),
                drug.getKdCode(),
                drug.getName(),
                drug.getIngredient(),
                drug.getEfficacy(),
                drug.getDosage(),
                drug.getSideEffect(),
                drug.getForm(),
                drug.getCompany(),
                drug.getSource(),
                imageUrl
        );
    }

    public static DrugDetailResponse from(Drug drug) {
        return from(drug, drug.getItemImage());
    }
}
