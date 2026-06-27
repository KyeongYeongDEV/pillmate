package com.pillmate.prescription.infrastructure.persistence;

import com.pillmate.prescription.domain.model.PrescriptionInsight;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

interface PrescriptionInsightJpaRepository extends JpaRepository<PrescriptionInsight, Long> {
    List<PrescriptionInsight> findByPrescriptionIdOrderByCreatedAtDesc(Long prescriptionId);
    List<PrescriptionInsight> findByPrescriptionIdInOrderByCreatedAtDesc(Collection<Long> prescriptionIds);
}
