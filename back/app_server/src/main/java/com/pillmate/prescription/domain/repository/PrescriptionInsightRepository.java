package com.pillmate.prescription.domain.repository;

import com.pillmate.prescription.domain.model.PrescriptionInsight;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface PrescriptionInsightRepository {
    PrescriptionInsight save(PrescriptionInsight insight);
    List<PrescriptionInsight> findByPrescriptionId(Long prescriptionId);
    Map<Long, List<PrescriptionInsight>> findByPrescriptionIds(Collection<Long> prescriptionIds);
}
