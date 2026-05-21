package com.pillmate.prescription.infrastructure.persistence;

import com.pillmate.prescription.domain.model.Prescription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface PrescriptionJpaRepository extends JpaRepository<Prescription, Long> {
    List<Prescription> findAllByPatientId(Long patientId);
}
