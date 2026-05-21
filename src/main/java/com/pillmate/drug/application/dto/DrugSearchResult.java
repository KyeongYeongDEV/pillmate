package com.pillmate.drug.application.dto;

import com.pillmate.drug.domain.model.Drug;

public record DrugSearchResult(
        Long id,
        String kdCode,
        String name,
        String ingredient,
        String efficacy,
        String form
) {
    public static DrugSearchResult from(Drug drug) {
        return new DrugSearchResult(
                drug.getId(),
                drug.getKdCode(),
                drug.getName(),
                drug.getIngredient(),
                drug.getEfficacy(),
                drug.getForm()
        );
    }
}
