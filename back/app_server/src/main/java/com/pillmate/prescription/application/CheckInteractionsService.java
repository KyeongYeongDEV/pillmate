package com.pillmate.prescription.application;

import com.pillmate.prescription.application.dto.InteractionWarning;
import com.pillmate.prescription.application.port.DrugInteractionPort;
import com.pillmate.prescription.application.port.DrugLookupPort;
import com.pillmate.prescription.domain.model.InteractionSeverity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CheckInteractionsService implements CheckInteractionsUseCase {

    private final DrugInteractionPort drugInteractionPort;
    private final DrugLookupPort drugLookupPort;

    @Override
    @Transactional(readOnly = true)
    public List<InteractionWarning> check(List<String> kdCodes) {
        if (kdCodes.size() < 2) return List.of();
        return drugInteractionPort.findByKdCodes(kdCodes).stream()
                .map(this::toWarning)
                .toList();
    }

    private InteractionWarning toWarning(DrugInteractionPort.DrugInteractionRecord r) {
        return new InteractionWarning(
                r.drugCodeA(), r.drugCodeB(),
                resolveName(r.drugCodeA()), resolveName(r.drugCodeB()),
                InteractionSeverity.valueOf(r.severity()),
                r.description(), r.source());
    }

    private String resolveName(String kdCode) {
        return drugLookupPort.findByKdCode(kdCode)
                .map(DrugLookupPort.DrugSummary::name)
                .orElse(kdCode);
    }
}
