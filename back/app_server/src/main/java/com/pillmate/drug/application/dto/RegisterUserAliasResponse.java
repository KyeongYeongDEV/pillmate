package com.pillmate.drug.application.dto;

public record RegisterUserAliasResponse(
        Long aliasId,
        String alias,
        String itemSeq,
        String source,
        int confidence,
        boolean isVerified) {}
