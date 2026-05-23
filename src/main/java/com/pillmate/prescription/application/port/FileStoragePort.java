package com.pillmate.prescription.application.port;

import java.time.Instant;

public interface FileStoragePort {
    PresignedUploadUrl generatePutUrl(String objectKey);
    String generateGetUrl(String objectKey);
    String issueDownloadUrl(String objectKey, java.time.Duration ttl);

    record PresignedUploadUrl(String url, Instant expiresAt) {}
}

