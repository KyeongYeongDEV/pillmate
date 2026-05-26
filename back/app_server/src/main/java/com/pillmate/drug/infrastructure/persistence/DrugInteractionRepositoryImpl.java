package com.pillmate.drug.infrastructure.persistence;

import com.pillmate.drug.domain.model.DrugInteraction;
import com.pillmate.drug.domain.repository.DrugInteractionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
class DrugInteractionRepositoryImpl implements DrugInteractionRepository {

    private final DrugInteractionJpaRepository jpaRepository;

    @Override
    public List<DrugInteraction> findByKdCodes(List<String> codes) {
        return jpaRepository.findByKdCodes(codes);
    }

    @Override
    public DrugInteraction save(DrugInteraction interaction) {
        return jpaRepository.save(interaction);
    }
}
