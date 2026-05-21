package com.pillmate.prescription.infrastructure.s3;

import com.pillmate.prescription.application.port.FileStoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;

@Component
@RequiredArgsConstructor
class S3Adapter implements FileStoragePort {

    private final S3Presigner s3Presigner;

    @Value("${cloud.s3.bucket}")
    private String bucket;

    @Value("${cloud.s3.presigned-put-ttl-minutes:5}")
    private int putTtlMinutes;

    @Value("${cloud.s3.presigned-get-ttl-hours:1}")
    private int getTtlHours;

    @Override
    public String generatePutUrl(String objectKey) {
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType("image/jpeg")
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(putTtlMinutes))
                .putObjectRequest(putRequest)
                .build();

        return s3Presigner.presignPutObject(presignRequest).url().toString();
    }

    @Override
    public String generateGetUrl(String objectKey) {
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofHours(getTtlHours))
                .getObjectRequest(getRequest)
                .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }
}
