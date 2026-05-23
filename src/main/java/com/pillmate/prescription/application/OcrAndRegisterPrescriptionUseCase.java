package com.pillmate.prescription.application;

import com.pillmate.prescription.application.dto.RegisterPrescriptionResponse;
import com.pillmate.prescription.application.port.FileStoragePort;
import com.pillmate.prescription.application.port.OcrPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class OcrAndRegisterPrescriptionUseCase {

    private static final Duration OCR_DOWNLOAD_TTL = Duration.ofMinutes(10);

    private final FileStoragePort fileStoragePort;
    private final OcrPort ocrPort;
    private final RegisterPrescriptionService registerPrescriptionService;

    public RegisterPrescriptionResponse ocrAndRegister(
            Long careGroupId, Long patientId, LocalDate prescribedAt, String imageKey) {
        // TODO: Implementation for T006c
        return null;
    }
}
