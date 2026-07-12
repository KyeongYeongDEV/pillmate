package com.pillmate.prescription.application;

import com.pillmate.common.security.PatientAccessGuard;
import com.pillmate.common.security.UserContext;
import com.pillmate.prescription.application.dto.LatestPrescriptionWithInsightResponse;
import com.pillmate.prescription.application.dto.PrescriptionInsightView;
import com.pillmate.prescription.application.port.ActiveMedicationPort;
import com.pillmate.prescription.domain.model.PrescribedDrug;
import com.pillmate.prescription.domain.model.Prescription;
import com.pillmate.prescription.domain.model.PrescriptionInsight;
import com.pillmate.prescription.domain.repository.PrescriptionInsightRepository;
import com.pillmate.prescription.domain.repository.PrescriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class GetActivePrescriptionsWithInsightUseCase {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int MAX_ITEMS = 10;

    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionInsightRepository prescriptionInsightRepository;
    private final ActiveMedicationPort activeMedicationPort;
    private final PatientAccessGuard patientAccessGuard;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<LatestPrescriptionWithInsightResponse> loadActiveForPatient(Long patientId) {
        patientAccessGuard.requireAccess(UserContext.get(), patientId);
        Set<Long> activeIds = activeMedicationPort.findActivePrescriptionIds(patientId, today());
        if (activeIds.isEmpty()) {
            return List.of();
        }
        return buildResponses(patientId, activeIds);
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(KST));
    }

    private List<LatestPrescriptionWithInsightResponse> buildResponses(Long patientId, Set<Long> activeIds) {
        Map<Long, List<PrescriptionInsight>> insightsByPrescription =
                prescriptionInsightRepository.findByPrescriptionIds(activeIds);
        return prescriptionRepository.findAllByPatientId(patientId).stream()
                .filter(prescription -> activeIds.contains(prescription.getId()))
                .filter(prescription -> hasInsight(insightsByPrescription, prescription.getId()))
                .sorted(Comparator.comparing(Prescription::getPrescribedAt)
                        .thenComparing(Prescription::getId).reversed())
                .limit(MAX_ITEMS)
                .map(prescription -> toResponse(prescription, insightsByPrescription))
                .toList();
    }

    private boolean hasInsight(Map<Long, List<PrescriptionInsight>> insightsByPrescription, Long prescriptionId) {
        List<PrescriptionInsight> insights = insightsByPrescription.get(prescriptionId);
        return insights != null && !insights.isEmpty();
    }

    private LatestPrescriptionWithInsightResponse toResponse(
            Prescription prescription, Map<Long, List<PrescriptionInsight>> insightsByPrescription) {
        List<PrescribedDrug> drugs = prescription.getDrugs();
        return new LatestPrescriptionWithInsightResponse(
                prescription.getId(), prescription.getPrescribedAt(), drugs.size(),
                primaryDrugName(drugs), views(insightsByPrescription.get(prescription.getId())));
    }

    private String primaryDrugName(List<PrescribedDrug> drugs) {
        return drugs.isEmpty() ? null : drugs.get(0).getNameRaw();
    }

    private List<PrescriptionInsightView> views(List<PrescriptionInsight> insights) {
        return insights.stream().map(PrescriptionInsightView::from).toList();
    }
}
