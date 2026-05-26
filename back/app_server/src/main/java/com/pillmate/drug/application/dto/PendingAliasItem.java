package com.pillmate.drug.application.dto;

public record PendingAliasItem(
        Long id,
        String alias,
        String aliasJamo,
        String itemSeq,
        String source,
        int confidence,
        boolean isVerified) {}
