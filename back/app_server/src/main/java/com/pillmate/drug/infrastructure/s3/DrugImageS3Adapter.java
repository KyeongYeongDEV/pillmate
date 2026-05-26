package com.pillmate.drug.infrastructure.s3;

import com.pillmate.drug.application.port.DrugImageStoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;

@Component
@RequiredArgsConstructor
class DrugImageS3Adapter implements DrugImageStoragePort {

    private static final String CONTENT_TYPE = "image/jpeg";

    private final S3Presigner s3Presigner;

    @Value("${cloud.s3.bucket}")
    private String bucket;

    @Override
    public String issueViewUrl(String objectKey, Duration ttl) {
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .responseContentType(CONTENT_TYPE)
                .responseContentDisposition("inline")
                .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(getRequest)
                .build();
        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }
}
