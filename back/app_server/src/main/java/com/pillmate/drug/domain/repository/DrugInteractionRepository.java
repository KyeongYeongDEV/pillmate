package com.pillmate.drug.domain.repository;

import com.pillmate.drug.domain.model.DrugInteraction;

import java.util.List;

public interface DrugInteractionRepository {

    List<DrugInteraction> findByKdCodes(List<String> codes);

    DrugInteraction save(DrugInteraction interaction);
}
