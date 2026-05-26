package com.pillmate.prescription.application.dto;

import com.pillmate.prescription.domain.model.OcrStatus;

import java.util.List;

public record RegisterPrescriptionResponse(
        Long prescriptionId,
        OcrStatus ocrStatus,
        List<RegisteredDrugItem> items,
        int unresolvedCount
) {
    public RegisterPrescriptionResponse(Long prescriptionId, OcrStatus ocrStatus, List<RegisteredDrugItem> items) {
        this(prescriptionId, ocrStatus, items, 0);
    }
}
