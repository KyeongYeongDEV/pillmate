package com.pillmate.prescription.application;

import com.pillmate.prescription.application.dto.UploadUrlResponse;
import com.pillmate.prescription.application.port.FileStoragePort;
import com.pillmate.prescription.application.port.FileStoragePort.PresignedUploadUrl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@DisplayName("GetUploadUrlUseCase — S3 Pre-signed PUT URL 발급")
@ExtendWith(MockitoExtension.class)
class GetUploadUrlUseCaseTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-05-23T03:00:00Z");

    @Mock FileStoragePort fileStoragePort;

    private final Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    private GetUploadUrlUseCase sut() {
        return new GetUploadUrlUseCase(fileStoragePort, fixedClock);
    }

    @Test
    @DisplayName("UUID 기반 객체키 + presigned URL + expiresAt 반환")
    void issue_returnsUuidKey_withExpiresAt() {
        Instant expiresAt = FIXED_NOW.plusSeconds(300);
        given(fileStoragePort.generatePutUrl(anyString()))
                .willReturn(new PresignedUploadUrl("https://s3.amazonaws.com/pillmate/prescriptions/abc.jpg?sig=x", expiresAt));

        UploadUrlResponse response = sut().issue(1L);

        assertThat(response.uploadUrl()).startsWith("https://s3");
        assertThat(response.objectKey()).startsWith("prescriptions/").endsWith(".jpg");
        assertThat(response.expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    @DisplayName("객체키는 yyyy/MM 경로 포함")
    void issue_keyContainsYearMonthPath() {
        given(fileStoragePort.generatePutUrl(anyString()))
                .willReturn(new PresignedUploadUrl("https://presigned", FIXED_NOW.plusSeconds(300)));

        UploadUrlResponse response = sut().issue(1L);

        assertThat(response.objectKey()).startsWith("prescriptions/2026/05/");
    }

    @Test
    @DisplayName("객체키에 환자 식별자가 포함되지 않는다")
    void issue_keyDoesNotContainPatientIdentifier() {
        Long patientLikeArg = 99L;
        given(fileStoragePort.generatePutUrl(anyString()))
                .willReturn(new PresignedUploadUrl("https://presigned", FIXED_NOW.plusSeconds(300)));

        UploadUrlResponse response = sut().issue(patientLikeArg);

        assertThat(response.objectKey()).doesNotContain(String.valueOf(patientLikeArg));
    }
}
