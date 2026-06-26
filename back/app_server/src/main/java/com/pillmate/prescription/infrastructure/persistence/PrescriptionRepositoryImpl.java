package com.pillmate.prescription.infrastructure.persistence;

import com.pillmate.prescription.domain.model.Prescription;
import com.pillmate.prescription.domain.repository.PrescriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
class PrescriptionRepositoryImpl implements PrescriptionRepository {

    private final PrescriptionJpaRepository jpa;

    @Override public Prescription save(Prescription p) { return jpa.save(p); }
    @Override public Optional<Prescription> findById(Long id) { return jpa.findByIdAndDeletedAtIsNull(id); }
    @Override public List<Prescription> findAllByPatientId(Long patientId) {
        return jpa.findAllByPatientIdAndDeletedAtIsNull(patientId);
    }
}
