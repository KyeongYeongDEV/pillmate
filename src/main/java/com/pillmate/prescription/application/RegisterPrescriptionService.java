package com.pillmate.prescription.application;

import com.pillmate.prescription.application.dto.DrugItem;
import com.pillmate.prescription.application.dto.RegisterPrescriptionCommand;
import com.pillmate.prescription.application.dto.RegisterPrescriptionResponse;
import com.pillmate.prescription.application.dto.RegisteredDrugItem;
import com.pillmate.prescription.application.exception.EmptyPrescriptionItemsException;
import com.pillmate.prescription.application.port.DrugLookupPort;
import com.pillmate.prescription.application.port.DrugLookupPort.DrugSummary;
import com.pillmate.prescription.domain.model.PrescribedDrug;
import com.pillmate.prescription.domain.model.Prescription;
import com.pillmate.prescription.domain.repository.PrescriptionRepository;
import com.pillmate.common.security.CareGroupGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegisterPrescriptionService {

    private final PrescriptionRepository prescriptionRepository;
    private final DrugLookupPort drugLookupPort;
    private final CareGroupGuard careGroupGuard;

    @Transactional
    public RegisterPrescriptionResponse register(RegisterPrescriptionCommand command) {
        careGroupGuard.requireAccessible(command.careGroupId());
        requireNonEmptyItems(command.items());

        Prescription prescription = Prescription.create(
                command.careGroupId(), command.patientId(),
                command.imageKey(), command.prescribedAt());

        List<RegisteredDrugItem> registered = appendDrugsAndCollect(prescription, command.items());
        prescription.markOcrDone();

        Prescription saved = prescriptionRepository.save(prescription);
        log.info("PrescriptionRegistered prescriptionId={} ocrStatus={} itemCount={} unmatched={}",
                saved.getId(), saved.getOcrStatus(), registered.size(), countUnmatched(registered));

        return new RegisterPrescriptionResponse(saved.getId(), saved.getOcrStatus(), registered);
    }

    private void requireNonEmptyItems(List<DrugItem> items) {
        if (items == null || items.isEmpty()) {
            throw new EmptyPrescriptionItemsException();
        }
    }

    private List<RegisteredDrugItem> appendDrugsAndCollect(
            Prescription prescription, List<DrugItem> items) {
        List<RegisteredDrugItem> registered = new ArrayList<>();
        for (DrugItem item : items) {
            Optional<DrugSummary> drug = lookupDrugOrEmpty(item.kdCode());
            Long drugId = drug.map(DrugSummary::drugId).orElse(null);
            prescription.addDrug(toPrescribedDrug(drugId, item));
            registered.add(toRegisteredItem(item, drug));
        }
        return registered;
    }

    private Optional<DrugSummary> lookupDrugOrEmpty(String kdCode) {
        if (kdCode == null || kdCode.isBlank()) {
            return Optional.empty();
        }
        return drugLookupPort.findByKdCode(kdCode);
    }

    private RegisteredDrugItem toRegisteredItem(DrugItem item, Optional<DrugSummary> drug) {
        return new RegisteredDrugItem(
                drug.map(DrugSummary::drugId).orElse(null),
                drug.map(DrugSummary::kdCode).orElse(null),
                item.nameRaw(),
                drug.map(DrugSummary::name).orElse(null),
                item.confidence());
    }

    private PrescribedDrug toPrescribedDrug(Long drugId, DrugItem item) {
        return PrescribedDrug.builder()
                .drugId(drugId)
                .nameRaw(item.nameRaw())
                .doseAmount(item.doseAmount())
                .doseUnit(item.doseUnit())
                .frequency(item.frequency())
                .durationDays(item.durationDays())
                .confidence(item.confidence())
                .build();
    }

    private long countUnmatched(List<RegisteredDrugItem> items) {
        return items.stream().filter(it -> it.drugId() == null).count();
    }
}
