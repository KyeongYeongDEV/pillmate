package com.pillmate.prescription.application.dto;

import com.pillmate.prescription.domain.model.OcrStatus;

import java.util.List;

public record RegisterPrescriptionResponse(
        Long prescriptionId,
        OcrStatus ocrStatus,
        List<RegisteredDrugItem> items,
        int unresolvedCount,
        List<InteractionWarning> warnings
) {
    public RegisterPrescriptionResponse(Long prescriptionId, OcrStatus ocrStatus, List<RegisteredDrugItem> items) {
        this(prescriptionId, ocrStatus, items, 0, List.of());
    }

    public RegisterPrescriptionResponse(Long prescriptionId, OcrStatus ocrStatus,
                                        List<RegisteredDrugItem> items, int unresolvedCount) {
        this(prescriptionId, ocrStatus, items, unresolvedCount, List.of());
    }
}
