package com.pillmate.prescription.application.dto;

import java.time.Instant;

public record UploadUrlResponse(
        String uploadUrl,
        String objectKey,
        Instant expiresAt
) {}
