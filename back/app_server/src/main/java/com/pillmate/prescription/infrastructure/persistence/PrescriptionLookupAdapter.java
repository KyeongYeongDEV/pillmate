package com.pillmate.prescription.infrastructure.persistence;

import com.pillmate.prescription.application.port.PrescriptionLookupPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PrescriptionLookupAdapter implements PrescriptionLookupPort {

    private final PrescriptionJpaRepository prescriptionJpaRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<PrescriptionOwner> findOwner(Long prescriptionId) {
        return prescriptionJpaRepository.findById(prescriptionId)
                .map(p -> new PrescriptionOwner(
                        p.getPatientId(),
                        p.getCareGroupId(),
                        p.getPrescribedAt(),
                        prescriptionJpaRepository.findMaxDurationDays(prescriptionId)));
    }
}
