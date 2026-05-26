package com.pillmate.prescription.application.dto;

import com.pillmate.prescription.domain.model.InteractionSeverity;

public record InteractionWarning(
        String drugCodeA,
        String drugCodeB,
        String nameA,
        String nameB,
        InteractionSeverity severity,
        String description,
        String source
) {}
