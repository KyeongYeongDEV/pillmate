package com.pillmate.prescription.domain.repository;

import com.pillmate.prescription.domain.model.Prescription;

import java.util.List;
import java.util.Optional;

public interface PrescriptionRepository {
    Prescription save(Prescription prescription);
    Optional<Prescription> findById(Long id);
    List<Prescription> findAllByPatientId(Long patientId);
    Optional<Prescription> findLatestByPatientId(Long patientId);
}
