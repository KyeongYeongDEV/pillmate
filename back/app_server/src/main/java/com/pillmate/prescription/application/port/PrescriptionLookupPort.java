package com.pillmate.prescription.application.port;

import java.time.LocalDate;
import java.util.Optional;

public interface PrescriptionLookupPort {
    record PrescriptionOwner(Long patientId, Long careGroupId, LocalDate prescribedAt, int maxDurationDays) {}
    Optional<PrescriptionOwner> findOwner(Long prescriptionId);
}
