package com.pillmate.prescription.application.dto;

import com.pillmate.prescription.domain.model.OcrStatus;

import java.util.List;

public record RegisterPrescriptionResponse(
        Long prescriptionId,
        OcrStatus ocrStatus,
        List<RegisteredDrugItem> items
) {}
