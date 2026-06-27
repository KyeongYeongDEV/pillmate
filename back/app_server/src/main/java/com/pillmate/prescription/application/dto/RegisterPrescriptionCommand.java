package com.pillmate.prescription.application.dto;

import java.time.LocalDate;
import java.util.List;

public record RegisterPrescriptionCommand(
        Long patientId,
        LocalDate prescribedAt,
        String imageKey,
        List<DrugItem> items,
        ScheduleSpec scheduleSpec,
        String label,
        String memo,
        String symptom
) {
    public RegisterPrescriptionCommand(Long patientId, LocalDate prescribedAt,
                                       String imageKey, List<DrugItem> items) {
        this(patientId, prescribedAt, imageKey, items, null, null, null, null);
    }

    public RegisterPrescriptionCommand(Long patientId, LocalDate prescribedAt,
                                       String imageKey, List<DrugItem> items,
                                       ScheduleSpec scheduleSpec) {
        this(patientId, prescribedAt, imageKey, items, scheduleSpec, null, null, null);
    }
}
