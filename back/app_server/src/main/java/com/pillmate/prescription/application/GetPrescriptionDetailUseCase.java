package com.pillmate.prescription.application;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.common.security.PatientAccessGuard;
import com.pillmate.common.security.UserContext;
import com.pillmate.prescription.application.dto.PrescriptionDetailResponse;
import com.pillmate.prescription.application.dto.PrescriptionDetailResponse.DrugDetail;
import com.pillmate.prescription.application.port.DrugLookupPort;
import com.pillmate.prescription.application.port.FileStoragePort;
import com.pillmate.prescription.domain.model.PrescribedDrug;
import com.pillmate.prescription.domain.model.Prescription;
import com.pillmate.prescription.domain.repository.PrescriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetPrescriptionDetailUseCase {

    private final PrescriptionRepository prescriptionRepository;
    private final DrugLookupPort drugLookupPort;
    private final FileStoragePort fileStoragePort;
    private final PatientAccessGuard patientAccessGuard;

    @Transactional(readOnly = true)
    public PrescriptionDetailResponse detail(Long prescriptionId) {
        Prescription prescription = findOwnPrescription(prescriptionId);
        return new PrescriptionDetailResponse(
                prescription.getId(),
                prescription.getPrescribedAt(),
                prescription.getOcrStatus(),
                resolveImageUrl(prescription.getImageKey()),
                toDrugDetails(prescription.getDrugs()));
    }

    private Prescription findOwnPrescription(Long prescriptionId) {
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new PillmateException(ErrorCode.PRESCRIPTION_NOT_FOUND));
        patientAccessGuard.requireAccess(UserContext.get(), prescription.getPatientId());
        return prescription;
    }

    private String resolveImageUrl(String imageKey) {
        if (imageKey == null || imageKey.isBlank()) {
            return null;
        }
        return fileStoragePort.generateGetUrl(imageKey);
    }

    private List<DrugDetail> toDrugDetails(List<PrescribedDrug> drugs) {
        return drugs.stream().map(this::toDrugDetail).toList();
    }

    private DrugDetail toDrugDetail(PrescribedDrug drug) {
        DrugLookupPort.DrugSummary summary = resolveSummary(drug.getDrugId());
        return new DrugDetail(
                drug.getNameRaw(),
                summary != null ? summary.name() : null,
                summary != null ? summary.kdCode() : null,
                drug.getDoseAmount(),
                drug.getDoseUnit(),
                drug.getFrequency(),
                drug.getDurationDays(),
                drug.getConfidence());
    }

    private DrugLookupPort.DrugSummary resolveSummary(Long drugId) {
        if (drugId == null) {
            return null;
        }
        return drugLookupPort.findById(drugId).orElse(null);
    }
}
