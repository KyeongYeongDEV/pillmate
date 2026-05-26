package com.pillmate.prescription.infrastructure.s3;

import com.pillmate.prescription.application.port.FileStoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
class S3Adapter implements FileStoragePort {

    private static final String PUT_CONTENT_TYPE = "image/jpeg";

    private final S3Presigner s3Presigner;
    private final Clock clock;

    @Value("${cloud.s3.bucket}")
    private String bucket;

    @Value("${cloud.s3.presigned-put-ttl-minutes:5}")
    private int putTtlMinutes;

    @Value("${cloud.s3.presigned-get-ttl-hours:1}")
    private int getTtlHours;

    @Override
    public PresignedUploadUrl generatePutUrl(String objectKey) {
        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(buildPutPresignRequest(objectKey));
        Instant expiresAt = clock.instant().plus(Duration.ofMinutes(putTtlMinutes));
        return new PresignedUploadUrl(presigned.url().toString(), expiresAt);
    }

    @Override
    public String generateGetUrl(String objectKey) {
        return issueDownloadUrl(objectKey, Duration.ofHours(getTtlHours));
    }

    @Override
    public String issueDownloadUrl(String objectKey, Duration ttl) {
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .responseContentType(PUT_CONTENT_TYPE)
                .responseContentDisposition("inline")
                .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(getRequest)
                .build();
        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    private PutObjectPresignRequest buildPutPresignRequest(String objectKey) {
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(PUT_CONTENT_TYPE)
                .serverSideEncryption(ServerSideEncryption.AES256)
                .build();
        return PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(putTtlMinutes))
                .putObjectRequest(putRequest)
                .build();
    }
}
