package com.pillmate.prescription.application;

import com.pillmate.common.security.UserContext;
import com.pillmate.prescription.application.dto.PrescriptionSummary;
import com.pillmate.prescription.domain.model.PrescribedDrug;
import com.pillmate.prescription.domain.model.Prescription;
import com.pillmate.prescription.domain.repository.PrescriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GetPrescriptionsUseCase {

    private static final int SUMMARY_NAME_LIMIT = 3;

    private final PrescriptionRepository prescriptionRepository;

    @Transactional(readOnly = true)
    public List<PrescriptionSummary> list() {
        Long patientId = UserContext.get();
        return prescriptionRepository.findAllByPatientId(patientId).stream()
                .sorted(Comparator.comparing(Prescription::getPrescribedAt).reversed())
                .map(this::toSummary)
                .toList();
    }

    private PrescriptionSummary toSummary(Prescription prescription) {
        List<PrescribedDrug> drugs = prescription.getDrugs();
        return new PrescriptionSummary(
                prescription.getId(),
                prescription.getPrescribedAt(),
                prescription.getOcrStatus(),
                drugs.size(),
                summarizeNames(drugs),
                prescription.getCreatedAt());
    }

    private String summarizeNames(List<PrescribedDrug> drugs) {
        return drugs.stream()
                .limit(SUMMARY_NAME_LIMIT)
                .map(PrescribedDrug::getNameRaw)
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }
}
