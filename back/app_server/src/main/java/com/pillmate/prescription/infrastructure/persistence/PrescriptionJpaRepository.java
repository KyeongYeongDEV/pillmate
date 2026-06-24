package com.pillmate.prescription.infrastructure.persistence;

import com.pillmate.prescription.domain.model.Prescription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

interface PrescriptionJpaRepository extends JpaRepository<Prescription, Long> {
    List<Prescription> findAllByPatientId(Long patientId);

    @Query("SELECT COALESCE(MAX(d.durationDays), 0) FROM PrescribedDrug d WHERE d.prescription.id = :id")
    int findMaxDurationDays(@Param("id") Long prescriptionId);
}
