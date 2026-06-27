package com.pillmate.prescription.infrastructure.persistence;

import com.pillmate.prescription.domain.model.PrescriptionInsight;
import com.pillmate.prescription.domain.repository.PrescriptionInsightRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
class PrescriptionInsightRepositoryImpl implements PrescriptionInsightRepository {

    private final PrescriptionInsightJpaRepository jpa;

    @Override
    public PrescriptionInsight save(PrescriptionInsight insight) {
        return jpa.save(insight);
    }

    @Override
    public List<PrescriptionInsight> findByPrescriptionId(Long prescriptionId) {
        return jpa.findByPrescriptionIdOrderByCreatedAtDesc(prescriptionId);
    }

    @Override
    public Map<Long, List<PrescriptionInsight>> findByPrescriptionIds(Collection<Long> prescriptionIds) {
        if (prescriptionIds.isEmpty()) {
            return Map.of();
        }
        return jpa.findByPrescriptionIdInOrderByCreatedAtDesc(prescriptionIds).stream()
                .collect(Collectors.groupingBy(PrescriptionInsight::getPrescriptionId));
    }
}
