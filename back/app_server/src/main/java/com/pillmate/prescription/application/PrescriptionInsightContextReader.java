package com.pillmate.prescription.application;

import com.pillmate.prescription.application.port.DrugLookupPort;
import com.pillmate.prescription.application.port.DrugLookupPort.DrugSummary;
import com.pillmate.prescription.application.port.PrescriptionRecommendationPort.DrugContext;
import com.pillmate.prescription.domain.model.PrescribedDrug;
import com.pillmate.prescription.domain.model.Prescription;
import com.pillmate.prescription.domain.repository.PrescriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PrescriptionInsightContextReader {

    private final PrescriptionRepository prescriptionRepository;
    private final DrugLookupPort drugLookupPort;

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Optional<RecommendationContext> load(Long prescriptionId) {
        return prescriptionRepository.findById(prescriptionId).map(this::toContext);
    }

    private RecommendationContext toContext(Prescription prescription) {
        return new RecommendationContext(prescription.getPatientId(), toDrugContexts(prescription.getDrugs()));
    }

    private List<DrugContext> toDrugContexts(List<PrescribedDrug> drugs) {
        List<Long> matchedIds = drugs.stream()
                .map(PrescribedDrug::getDrugId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, DrugSummary> summaries = drugLookupPort.findByIds(matchedIds);
        return drugs.stream().map(drug -> toDrugContext(drug, summaries)).toList();
    }

    private DrugContext toDrugContext(PrescribedDrug drug, Map<Long, DrugSummary> summaries) {
        DrugSummary summary = drug.getDrugId() != null ? summaries.get(drug.getDrugId()) : null;
        return new DrugContext(
                summary != null ? summary.kdCode() : null,
                summary != null ? summary.name() : drug.getNameRaw(),
                drug.getDoseAmount(), drug.getDoseUnit(),
                drug.getFrequency(), drug.getDurationDays());
    }

    public record RecommendationContext(Long patientId, List<DrugContext> drugs) {}
}
