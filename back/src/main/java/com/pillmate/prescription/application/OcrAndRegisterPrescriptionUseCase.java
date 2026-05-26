package com.pillmate.prescription.application;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.prescription.application.dto.DrugItem;
import com.pillmate.prescription.application.dto.RegisterPrescriptionCommand;
import com.pillmate.prescription.application.dto.RegisterPrescriptionResponse;
import com.pillmate.prescription.application.port.FileStoragePort;
import com.pillmate.prescription.application.port.OcrPort;
import com.pillmate.prescription.application.port.OcrPort.OcrItem;
import com.pillmate.prescription.application.port.OcrPort.OcrResult;
import com.pillmate.common.security.CareGroupGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OcrAndRegisterPrescriptionUseCase {

    private static final Duration OCR_DOWNLOAD_TTL = Duration.ofMinutes(10);

    private final FileStoragePort fileStoragePort;
    private final OcrPort ocrPort;
    private final RegisterPrescriptionService registerPrescriptionService;
    private final CareGroupGuard careGroupGuard;

    public RegisterPrescriptionResponse ocrAndRegister(
            Long careGroupId, Long patientId, LocalDate prescribedAt, String imageKey) {
        careGroupGuard.requireAccessible(careGroupId);
        String downloadUrl = fileStoragePort.issueDownloadUrl(imageKey, OCR_DOWNLOAD_TTL);
        OcrResult ocrResult = ocrPort.extractFromImage(downloadUrl);
        
        validateOcrResult(ocrResult);
        
        RegisterPrescriptionCommand command = mapToRegisterCommand(
                careGroupId, patientId, prescribedAt, imageKey, ocrResult);
        
        return registerPrescriptionService.register(command);
    }

    private void validateOcrResult(OcrResult ocrResult) {
        if (ocrResult.items().isEmpty()) {
            throw new PillmateException(ErrorCode.OCR_EMPTY);
        }
    }

    private RegisterPrescriptionCommand mapToRegisterCommand(
            Long careGroupId, Long patientId, LocalDate prescribedAt, String imageKey, OcrResult ocrResult) {
        List<DrugItem> items = ocrResult.items().stream()
                .map(this::toDrugItem)
                .toList();
        return new RegisterPrescriptionCommand(careGroupId, patientId, prescribedAt, imageKey, items);
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
