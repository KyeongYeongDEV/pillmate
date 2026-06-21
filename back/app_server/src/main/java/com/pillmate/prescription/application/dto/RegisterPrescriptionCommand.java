package com.pillmate.prescription.application.dto;

import java.time.LocalDate;
import java.util.List;

public record RegisterPrescriptionCommand(
        Long patientId,
        LocalDate prescribedAt,
        String imageKey,
        List<DrugItem> items,
        ScheduleSpec scheduleSpec
) {
    public RegisterPrescriptionCommand(Long patientId, LocalDate prescribedAt,
                                       String imageKey, List<DrugItem> items) {
        this(patientId, prescribedAt, imageKey, items, null);
    }
}
