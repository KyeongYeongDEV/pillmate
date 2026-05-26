package com.pillmate.prescription.application.dto;

import com.pillmate.prescription.domain.model.CandidateDecisionType;

public record UnresolvedCandidateDto(
        Long id,
        int itemIndex,
        CandidateDecisionType decisionType,
        String reason,
        String optionsJson
) {}
