package com.pillmate.prescription.application;

import com.pillmate.prescription.application.dto.UploadUrlResponse;
import com.pillmate.prescription.application.port.FileStoragePort;
import com.pillmate.prescription.application.port.FileStoragePort.PresignedUploadUrl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GetUploadUrlUseCase {

    private static final String OBJECT_KEY_EXTENSION = ".jpg";
    private static final ZoneId KEY_ZONE = ZoneId.of("Asia/Seoul");

    private final FileStoragePort fileStoragePort;
    private final Clock clock;

    public UploadUrlResponse issue(Long careGroupId) {
        String objectKey = buildObjectKey();
        PresignedUploadUrl presigned = fileStoragePort.generatePutUrl(objectKey);
        log.info("PresignedUrlIssued careGroupId={} objectKey={}", careGroupId, objectKey);
        return new UploadUrlResponse(presigned.url(), objectKey, presigned.expiresAt());
    }

    private String buildObjectKey() {
        LocalDate today = LocalDate.now(clock.withZone(KEY_ZONE));
        return String.format("prescriptions/%04d/%02d/%s%s",
                today.getYear(), today.getMonthValue(),
                UUID.randomUUID(), OBJECT_KEY_EXTENSION);
    }
}
