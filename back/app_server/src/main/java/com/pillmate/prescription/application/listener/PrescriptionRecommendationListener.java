package com.pillmate.prescription.application.listener;

import com.pillmate.prescription.application.PrescriptionInsightContextReader;
import com.pillmate.prescription.application.PrescriptionInsightContextReader.RecommendationContext;
import com.pillmate.prescription.application.PrescriptionInsightPersistenceService;
import com.pillmate.prescription.application.port.PrescriptionRecommendationPort;
import com.pillmate.prescription.application.port.PrescriptionRecommendationPort.InsightDraft;
import com.pillmate.prescription.domain.event.PrescriptionRegistered;
import com.pillmate.prescription.domain.model.PrescriptionInsight;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PrescriptionRecommendationListener {

    private final PrescriptionInsightContextReader contextReader;
    private final PrescriptionRecommendationPort recommendationPort;
    private final PrescriptionInsightPersistenceService persistenceService;

    @Order(20)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(PrescriptionRegistered event) {
        try {
            generateAndPersist(event.prescriptionId());
        } catch (Exception e) {
            log.warn("prescription recommendation failed prescriptionId={} reason={}",
                    event.prescriptionId(), e.getClass().getSimpleName());
        }
    }

    private void generateAndPersist(Long prescriptionId) {
        RecommendationContext context = contextReader.load(prescriptionId).orElse(null);
        if (context == null) {
            return;
        }
        List<InsightDraft> drafts = recommendationPort.generate(
                prescriptionId, context.patientId(), context.drugs());
        List<PrescriptionInsight> insights = drafts.stream()
                .map(draft -> toInsight(prescriptionId, draft))
                .toList();
        if (insights.isEmpty()) {
            return;
        }
        persistenceService.saveAll(insights);
    }

    private PrescriptionInsight toInsight(Long prescriptionId, InsightDraft draft) {
        return PrescriptionInsight.create(prescriptionId, draft.type(), draft.severity(),
                draft.title(), draft.description(), draft.source(), draft.confidence());
    }
}
