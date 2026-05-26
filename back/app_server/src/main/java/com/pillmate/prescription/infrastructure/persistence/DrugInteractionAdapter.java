package com.pillmate.prescription.infrastructure.persistence;

import com.pillmate.drug.domain.model.DrugInteraction;
import com.pillmate.drug.infrastructure.persistence.DrugInteractionJpaRepository;
import com.pillmate.prescription.application.port.DrugInteractionPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
class DrugInteractionAdapter implements DrugInteractionPort {

    private final DrugInteractionJpaRepository jpaRepository;

    @Override
    public List<DrugInteractionRecord> findByKdCodes(List<String> kdCodes) {
        if (kdCodes.size() < 2) return List.of();
        return jpaRepository.findByKdCodes(kdCodes).stream()
                .map(this::toRecord)
                .toList();
    }

    private DrugInteractionRecord toRecord(DrugInteraction i) {
        return new DrugInteractionRecord(
                i.getDrugCodeA(), i.getDrugCodeB(),
                i.getSeverity(), i.getDescription(), i.getSource());
    }
}
