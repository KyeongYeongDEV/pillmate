package com.pillmate.prescription.application;

import com.pillmate.prescription.application.dto.UploadUrlResponse;
import com.pillmate.prescription.application.port.FileStoragePort;
import com.pillmate.prescription.domain.model.Prescription;
import com.pillmate.prescription.domain.repository.PrescriptionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@DisplayName("GetUploadUrlUseCase")
@ExtendWith(MockitoExtension.class)
class GetUploadUrlUseCaseTest {

    @Mock PrescriptionRepository prescriptionRepository;
    @Mock FileStoragePort fileStoragePort;
    @InjectMocks GetUploadUrlUseCase sut;

    @Test
    @DisplayName("처방전 저장 후 S3 PUT presigned URL을 반환한다")
    void getUploadUrl_returnsPrescriptionIdAndUrl() {
        Prescription saved = Prescription.create(1L, 2L, "prescriptions/test.jpg", LocalDate.now());
        given(prescriptionRepository.save(any())).willReturn(saved);
        given(fileStoragePort.generatePutUrl(anyString()))
                .willReturn("https://s3.ap-northeast-2.amazonaws.com/pillmate-prescriptions/prescriptions/test.jpg?X-Amz-Signature=abc");

        UploadUrlResponse response = sut.getUploadUrl(1L, 2L, LocalDate.now());

        verify(prescriptionRepository).save(any());
        verify(fileStoragePort).generatePutUrl(anyString());
        assertThat(response.uploadUrl()).startsWith("https://s3");
        assertThat(response.objectKey()).startsWith("prescriptions/").endsWith(".jpg");
    }

    @Test
    @DisplayName("objectKey에 환자 식별자가 포함되지 않는다")
    void getUploadUrl_objectKeyHasNoPatientId() {
        Prescription saved = Prescription.create(1L, 99L, "prescriptions/uuid.jpg", LocalDate.now());
        given(prescriptionRepository.save(any())).willReturn(saved);
        given(fileStoragePort.generatePutUrl(anyString())).willReturn("https://presigned-url");

        UploadUrlResponse response = sut.getUploadUrl(1L, 99L, LocalDate.now());

        assertThat(response.objectKey()).doesNotContain("99");
    }
}
