package com.pillmate.prescription.infrastructure.s3;

import com.pillmate.prescription.application.port.FileStoragePort;
import com.pillmate.prescription.application.port.FileStoragePort.PresignedUploadUrl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@DisplayName("S3Adapter — Pre-signed URL 발급 (SSE-S3 강제)")
class S3AdapterTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-05-23T03:00:00Z");
    private static final int PUT_TTL_MINUTES = 5;

    private S3Presigner s3Presigner;
    private S3Adapter sut;

    @BeforeEach
    void setUp() {
        s3Presigner = mock(S3Presigner.class);
        sut = new S3Adapter(s3Presigner, Clock.fixed(FIXED_NOW, ZoneOffset.UTC));
        ReflectionTestUtils.setField(sut, "bucket", "pillmate-prescriptions");
        ReflectionTestUtils.setField(sut, "putTtlMinutes", PUT_TTL_MINUTES);
        ReflectionTestUtils.setField(sut, "getTtlHours", 1);
    }

    @Test
    @DisplayName("PUT presign 요청은 SSE-S3(AES256) 헤더를 포함하여 서명된다")
    void generatePutUrl_signsWithSseHeader() throws Exception {
        ArgumentCaptor<PutObjectPresignRequest> captor =
                ArgumentCaptor.forClass(PutObjectPresignRequest.class);
        PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
        given(presigned.url()).willReturn(URI.create("https://s3.test/pillmate-prescriptions/key?sig=x").toURL());
        given(s3Presigner.presignPutObject(captor.capture())).willReturn(presigned);

        PresignedUploadUrl result = sut.generatePutUrl("prescriptions/2026/05/uuid.jpg");

        PutObjectPresignRequest captured = captor.getValue();
        assertThat(captured.putObjectRequest().serverSideEncryption())
                .isEqualTo(ServerSideEncryption.AES256);
        assertThat(captured.signatureDuration()).isEqualTo(Duration.ofMinutes(PUT_TTL_MINUTES));
        assertThat(result.url()).startsWith("https://s3.test/");
        assertThat(result.expiresAt()).isEqualTo(FIXED_NOW.plus(Duration.ofMinutes(PUT_TTL_MINUTES)));
    }

    @Test
    @DisplayName("PUT presign 객체 키와 콘텐츠 타입이 요청에 반영된다")
    void generatePutUrl_includesObjectKeyAndContentType() throws Exception {
        ArgumentCaptor<PutObjectPresignRequest> captor =
                ArgumentCaptor.forClass(PutObjectPresignRequest.class);
        PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
        given(presigned.url()).willReturn(URI.create("https://s3.test/pillmate-prescriptions/key").toURL());
        given(s3Presigner.presignPutObject(captor.capture())).willReturn(presigned);

        sut.generatePutUrl("prescriptions/2026/05/abc.jpg");

        PutObjectPresignRequest captured = captor.getValue();
        assertThat(captured.putObjectRequest().key()).isEqualTo("prescriptions/2026/05/abc.jpg");
        assertThat(captured.putObjectRequest().contentType()).isEqualTo("image/jpeg");
        assertThat(captured.putObjectRequest().bucket()).isEqualTo("pillmate-prescriptions");
    }
}
