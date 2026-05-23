package com.pillmate.prescription.application.port;

import java.time.Instant;

public interface FileStoragePort {

    PresignedUploadUrl generatePutUrl(String objectKey);

    String generateGetUrl(String objectKey);

    record PresignedUploadUrl(String url, Instant expiresAt) {}
}
