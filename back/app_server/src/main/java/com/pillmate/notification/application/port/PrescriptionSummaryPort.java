package com.pillmate.notification.application.port;

import java.time.LocalDate;
import java.util.Optional;

public interface PrescriptionSummaryPort {

    Optional<PrescriptionSummary> findById(Long prescriptionId);

    record PrescriptionSummary(LocalDate prescribedAt, String label) {}
}
