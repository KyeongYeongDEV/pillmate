package com.pillmate.prescription.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.common.security.CareGroupGuard;
import com.pillmate.common.security.PatientAccessGuard;
import com.pillmate.common.security.UserContext;
import com.pillmate.prescription.application.port.DrugLookupPort;
import com.pillmate.prescription.application.port.OcrMatchLogPort;
import com.pillmate.prescription.domain.model.PrescribedDrugCandidate;
import com.pillmate.prescription.domain.model.Prescription;
import com.pillmate.prescription.domain.repository.PrescriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResolveCandidateService implements ResolveCandidateUseCase {

    private final PrescriptionRepository prescriptionRepository;
    private final CareGroupGuard careGroupGuard;
    private final PatientAccessGuard patientAccessGuard;
    private final ObjectMapper objectMapper;
    private final DrugLookupPort drugLookupPort;
    private final OcrMatchLogPort ocrMatchLogPort;

    @Transactional
    @Override
    public void resolve(Long prescriptionId, int itemIndex, Long selectedDrugId, Long resolverId) {
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new PillmateException(ErrorCode.PRESCRIPTION_NOT_FOUND));
        patientAccessGuard.requireAccess(UserContext.get(), prescription.getPatientId());

        PrescribedDrugCandidate candidate = findCandidate(prescription, itemIndex);
        validateNotResolved(candidate);
        validateOptionExists(candidate.getOptionsJson(), selectedDrugId);
        candidate.resolve(selectedDrugId, resolverId);
        prescriptionRepository.save(prescription);
        updateMatchLog(prescription, candidate.getItemIndex(), selectedDrugId);
    }

    private void updateMatchLog(Prescription prescription, int itemIndex, Long selectedDrugId) {
        try {
            String imageKey = prescription.getImageKey();
            if (imageKey == null) return;
            String rawOcrText = prescription.getDrugs().get(itemIndex).getNameRaw();
            String kdCode = drugLookupPort.findById(selectedDrugId)
                    .map(DrugLookupPort.DrugSummary::kdCode)
                    .orElse(null);
            if (kdCode == null) return;
            ocrMatchLogPort.updateUserCorrection(imageKey, rawOcrText, kdCode);
        } catch (Exception e) {
            log.warn("match log update skipped (best-effort): {}", e.getMessage());
        }
    }

    private PrescribedDrugCandidate findCandidate(Prescription prescription, int itemIndex) {
        return prescription.getCandidates().stream()
                .filter(c -> c.getItemIndex() == itemIndex)
                .findFirst()
                .orElseThrow(() -> new PillmateException(ErrorCode.CANDIDATE_NOT_FOUND));
    }

    private void validateNotResolved(PrescribedDrugCandidate candidate) {
        if (candidate.isResolved()) {
            throw new PillmateException(ErrorCode.CANDIDATE_ALREADY_RESOLVED);
        }
    }

    private void validateOptionExists(String optionsJson, Long selectedDrugId) {
        try {
            List<Map<String, Object>> options = objectMapper.readValue(
                    optionsJson, new TypeReference<>() {});
            boolean found = options.stream()
                    .map(opt -> opt.get("drugId"))
                    .filter(v -> v instanceof Number)
                    .anyMatch(v -> selectedDrugId.equals(((Number) v).longValue()));
            if (!found) {
                throw new PillmateException(ErrorCode.CANDIDATE_OPTION_INVALID);
            }
        } catch (JsonProcessingException e) {
            throw new PillmateException(ErrorCode.CANDIDATE_OPTION_INVALID);
        }
    }
}
