package com.pillmate.drug.application.dto;

public record VerifyAliasResponse(
        Long id,
        String alias,
        String itemSeq,
        boolean isVerified,
        int confidence) {}
