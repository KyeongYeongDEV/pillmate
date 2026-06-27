package com.pillmate.prescription.application;

import com.pillmate.common.security.PatientAccessGuard;
import com.pillmate.common.security.UserContext;
import com.pillmate.prescription.application.dto.LatestPrescriptionWithInsightResponse;
import com.pillmate.prescription.application.dto.PrescriptionInsightView;
import com.pillmate.prescription.domain.model.PrescribedDrug;
import com.pillmate.prescription.domain.model.Prescription;
import com.pillmate.prescription.domain.repository.PrescriptionInsightRepository;
import com.pillmate.prescription.domain.repository.PrescriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetLatestPrescriptionWithInsightUseCase {

    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionInsightRepository prescriptionInsightRepository;
    private final PatientAccessGuard patientAccessGuard;

    @Transactional(readOnly = true)
    public LatestPrescriptionWithInsightResponse loadLatestForPatient(Long patientId) {
        patientAccessGuard.requireAccess(UserContext.get(), patientId);
        Prescription latest = prescriptionRepository.findLatestByPatientId(patientId).orElse(null);
        if (latest == null) {
            return null;
        }
        return toResponse(latest);
    }

    private LatestPrescriptionWithInsightResponse toResponse(Prescription prescription) {
        List<PrescribedDrug> drugs = prescription.getDrugs();
        return new LatestPrescriptionWithInsightResponse(
                prescription.getId(), prescription.getPrescribedAt(),
                drugs.size(), resolvePrimaryDrugName(drugs), resolveInsights(prescription.getId()));
    }

    private String resolvePrimaryDrugName(List<PrescribedDrug> drugs) {
        return drugs.isEmpty() ? null : drugs.get(0).getNameRaw();
    }

    private List<PrescriptionInsightView> resolveInsights(Long prescriptionId) {
        List<PrescriptionInsightView> views = prescriptionInsightRepository.findByPrescriptionId(prescriptionId)
                .stream().map(PrescriptionInsightView::from).toList();
        return views.isEmpty() ? null : views;
    }
}
