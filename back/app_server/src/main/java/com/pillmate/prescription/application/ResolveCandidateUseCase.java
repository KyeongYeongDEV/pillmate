package com.pillmate.prescription.application;

public interface ResolveCandidateUseCase {
    void resolve(Long prescriptionId, int itemIndex, Long selectedDrugId, Long resolverId);
}
