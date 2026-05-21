package com.pillmate.prescription.application.dto;

public record UploadUrlResponse(
        Long prescriptionId,
        String uploadUrl,
        String objectKey
) {}
