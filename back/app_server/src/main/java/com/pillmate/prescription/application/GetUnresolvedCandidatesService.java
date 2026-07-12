package com.pillmate.prescription.application;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.common.security.PatientAccessGuard;
import com.pillmate.common.security.UserContext;
import com.pillmate.prescription.application.dto.UnresolvedCandidateDto;
import com.pillmate.prescription.domain.model.PrescribedDrugCandidate;
import com.pillmate.prescription.domain.model.Prescription;
import com.pillmate.prescription.domain.repository.PrescriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetUnresolvedCandidatesService implements GetUnresolvedCandidatesUseCase {

    private final PrescriptionRepository prescriptionRepository;
    private final PatientAccessGuard patientAccessGuard;

    @Transactional(readOnly = true)
    @Override
    public List<UnresolvedCandidateDto> getUnresolved(Long prescriptionId) {
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new PillmateException(ErrorCode.PRESCRIPTION_NOT_FOUND));
        patientAccessGuard.requireAccess(UserContext.get(), prescription.getPatientId());
        return prescription.getCandidates().stream()
                .filter(c -> !c.isResolved())
                .map(this::toDto)
                .toList();
    }

    private UnresolvedCandidateDto toDto(PrescribedDrugCandidate c) {
        return new UnresolvedCandidateDto(
                c.getId(), c.getItemIndex(), c.getDecisionType(), c.getReason(), c.getOptionsJson());
    }
}
