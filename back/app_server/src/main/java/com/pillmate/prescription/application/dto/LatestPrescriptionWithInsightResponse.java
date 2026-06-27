package com.pillmate.prescription.application.dto;

import java.time.LocalDate;
import java.util.List;

public record LatestPrescriptionWithInsightResponse(
        Long prescriptionId,
        LocalDate prescribedAt,
        int drugCount,
        String primaryDrugName,
        List<PrescriptionInsightView> insights
) {}
