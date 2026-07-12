package com.pillmate.prescription.application.port;

import java.time.LocalDate;
import java.util.Set;

public interface ActiveMedicationPort {

    Set<Long> findActivePrescriptionIds(Long patientId, LocalDate today);
}
