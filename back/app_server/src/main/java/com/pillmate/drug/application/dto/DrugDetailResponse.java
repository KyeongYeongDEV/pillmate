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
        String imageUrl,
        String className
) {
    public static DrugDetailResponse from(Drug drug, String imageUrl) {
        return new DrugDetailResponse(
                drug.getId(),
                drug.getKdCode(),
                drug.getName(),
                resolveIngredient(drug),
                drug.getEfficacy(),
                drug.getDosage(),
                drug.getSideEffect(),
                drug.getForm(),
                drug.getCompany(),
                drug.getSource(),
                imageUrl,
                drug.getClassName()
        );
    }

    public static DrugDetailResponse from(Drug drug) {
        return from(drug, drug.getItemImage());
    }

    // main_ingr(79% 적재) 우선, 비어 있으면 legacy ingredient 컬럼으로 fallback
    private static String resolveIngredient(Drug drug) {
        return notBlank(drug.getMainIngr()) ? drug.getMainIngr() : drug.getIngredient();
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
