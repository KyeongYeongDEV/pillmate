package com.pillmate.prescription.application.dto;

import java.time.LocalDate;
import java.util.List;

public record RegisterPrescriptionCommand(
        Long careGroupId,
        Long patientId,
        LocalDate prescribedAt,
        String imageKey,
        List<DrugItem> items
) {}
