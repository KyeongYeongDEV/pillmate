package com.pillmate.prescription.presentation.dto;

import jakarta.validation.constraints.NotNull;

public record UploadUrlRequest(
        @NotNull(message = "careGroupId는 필수입니다.") Long careGroupId
) {}
