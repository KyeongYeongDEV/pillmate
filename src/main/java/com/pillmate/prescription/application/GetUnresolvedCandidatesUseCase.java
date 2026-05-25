package com.pillmate.prescription.application;

import com.pillmate.prescription.application.dto.UnresolvedCandidateDto;

import java.util.List;

public interface GetUnresolvedCandidatesUseCase {
    List<UnresolvedCandidateDto> getUnresolved(Long prescriptionId);
}
