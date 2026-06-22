package com.pillmate.prescription.application;

import com.pillmate.prescription.application.dto.DrugItem;
import com.pillmate.prescription.application.dto.RegisterPrescriptionCommand;
import com.pillmate.prescription.application.dto.RegisterPrescriptionResponse;
import com.pillmate.prescription.application.port.OcrPort.OcrItem;
import com.pillmate.prescription.application.port.OcrPort.OcrResult;
import com.pillmate.common.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OcrAndRegisterPrescriptionUseCase {

    private final OcrExtractService ocrExtractService;
    private final RegisterPrescriptionService registerPrescriptionService;

    public RegisterPrescriptionResponse ocrAndRegister(LocalDate prescribedAt, String imageKey) {
        Long patientId = UserContext.get();
        OcrResult ocrResult = ocrExtractService.extractAndValidate(imageKey);
        RegisterPrescriptionCommand command = mapToRegisterCommand(patientId, prescribedAt, imageKey, ocrResult);
        return registerPrescriptionService.register(command);
    }

    private RegisterPrescriptionCommand mapToRegisterCommand(
            Long patientId, LocalDate prescribedAt, String imageKey, OcrResult ocrResult) {
        List<DrugItem> items = ocrResult.items().stream()
                .map(this::toDrugItem)
                .toList();
        return new RegisterPrescriptionCommand(patientId, prescribedAt, imageKey, items);
    }

    private DrugItem toDrugItem(OcrItem ocrItem) {
        return new DrugItem(
                ocrItem.kdCode(),
                ocrItem.nameRaw(),
                ocrItem.doseAmount(),
                ocrItem.doseUnit(),
                ocrItem.frequency(),
                ocrItem.durationDays(),
                ocrItem.confidence(),
                ocrItem.decision(),
                ocrItem.candidateOptionsJson()
        );
    }
}
