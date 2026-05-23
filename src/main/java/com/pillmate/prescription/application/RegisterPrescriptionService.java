package com.pillmate.prescription.application;

import com.pillmate.prescription.application.dto.DrugItem;
import com.pillmate.prescription.application.dto.RegisterPrescriptionCommand;
import com.pillmate.prescription.application.dto.RegisterPrescriptionResponse;
import com.pillmate.prescription.application.dto.RegisteredDrugItem;
import com.pillmate.prescription.application.exception.DrugNotMatchedException;
import com.pillmate.prescription.application.exception.EmptyPrescriptionItemsException;
import com.pillmate.prescription.application.port.DrugLookupPort;
import com.pillmate.prescription.application.port.DrugLookupPort.DrugSummary;
import com.pillmate.prescription.domain.model.PrescribedDrug;
import com.pillmate.prescription.domain.model.Prescription;
import com.pillmate.prescription.domain.repository.PrescriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegisterPrescriptionService {

    private final PrescriptionRepository prescriptionRepository;
    private final DrugLookupPort drugLookupPort;

    @Transactional
    public RegisterPrescriptionResponse register(RegisterPrescriptionCommand command) {
        requireNonEmptyItems(command.items());

        Prescription prescription = Prescription.create(
                command.careGroupId(), command.patientId(),
                command.imageKey(), command.prescribedAt());

        List<RegisteredDrugItem> registered = appendDrugsAndCollect(prescription, command.items());
        prescription.markOcrDone();

        Prescription saved = prescriptionRepository.save(prescription);
        log.info("PrescriptionRegistered prescriptionId={} ocrStatus={} itemCount={}",
                saved.getId(), saved.getOcrStatus(), registered.size());

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
            DrugSummary drug = lookupDrug(item.kdCode());
            prescription.addDrug(toPrescribedDrug(drug.drugId(), item));
            registered.add(new RegisteredDrugItem(
                    drug.drugId(), drug.kdCode(), drug.name(), item.confidence()));
        }
        return registered;
    }

    private DrugSummary lookupDrug(String kdCode) {
        return drugLookupPort.findByKdCode(kdCode)
                .orElseThrow(() -> new DrugNotMatchedException(kdCode));
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
}
