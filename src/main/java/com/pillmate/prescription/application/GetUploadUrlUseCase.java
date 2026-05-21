package com.pillmate.prescription.application;

import com.pillmate.prescription.application.dto.UploadUrlResponse;
import com.pillmate.prescription.application.port.FileStoragePort;
import com.pillmate.prescription.domain.model.Prescription;
import com.pillmate.prescription.domain.repository.PrescriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetUploadUrlUseCase {

    private final PrescriptionRepository prescriptionRepository;
    private final FileStoragePort fileStoragePort;

    @Transactional
    public UploadUrlResponse getUploadUrl(Long careGroupId, Long patientId, LocalDate prescribedAt) {
        // 환자 식별자 없는 UUID 기반 키 (개인정보 보호)
        String objectKey = "prescriptions/" + UUID.randomUUID() + ".jpg";

        Prescription prescription = prescriptionRepository.save(
                Prescription.create(careGroupId, patientId, objectKey, prescribedAt));

        String uploadUrl = fileStoragePort.generatePutUrl(objectKey);

        return new UploadUrlResponse(prescription.getId(), uploadUrl, objectKey);
    }
}
