package com.pillmate.prescription.infrastructure.s3;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("live")
@DisplayName("S3Adapter — 실제 AWS 자격증명으로 presigned URL 생성")
class S3AdapterLiveTest {

    @Test
    @DisplayName("유효한 자격증명으로 PUT presigned URL이 생성된다")
    void generatePutUrl_withRealCredentials() {
        String accessKey = System.getenv("AWS_ACCESS_KEY_ID");
        String secretKey = System.getenv("AWS_SECRET_ACCESS_KEY");
        String bucket    = System.getenv().getOrDefault("S3_BUCKET_NAME", "pillmate-prescriptions");

        // 자격증명이 없으면 스킵 (CI 환경)
        org.junit.jupiter.api.Assumptions.assumeTrue(
                accessKey != null && !accessKey.isBlank() && !accessKey.startsWith("your_"),
                "AWS credentials not set — skipping live test");

        S3Presigner presigner = S3Presigner.builder()
                .region(Region.AP_NORTHEAST_2)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .build();

        PutObjectPresignRequest req = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(5))
                .putObjectRequest(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key("test/presign-check.jpg")
                        .contentType("image/jpeg")
                        .build())
                .build();

        String url = presigner.presignPutObject(req).url().toString();

        System.out.println("✅ Presigned PUT URL: " + url.substring(0, 80) + "...");
        assertThat(url)
                .startsWith("https://")
                .contains(bucket)
                .contains("X-Amz-Signature");

        presigner.close();
    }
}
