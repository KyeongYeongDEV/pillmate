package com.pillmate.schedule.application;

import java.time.LocalDate;

public interface UpdatePrescriptionPeriodUseCase {
    void update(Long prescriptionId, LocalDate newEndDate);
}
