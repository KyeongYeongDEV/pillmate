package com.pillmate.prescription.application;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.prescription.application.port.FileStoragePort;
import com.pillmate.prescription.application.port.OcrPort;
import com.pillmate.prescription.application.port.OcrPort.OcrItem;
import com.pillmate.prescription.application.port.OcrPort.OcrResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@DisplayName("OcrExtractService — imageKey→downloadUrl→OCR→검증 공유 서비스")
@ExtendWith(MockitoExtension.class)
class OcrExtractServiceTest {

    @Mock FileStoragePort fileStoragePort;
    @Mock OcrPort ocrPort;
    @InjectMocks OcrExtractService sut;

    private static final String IMAGE_KEY = "prescriptions/2026/06/uuid.jpg";
    private static final String DOWNLOAD_URL = "https://s3.test/presigned?sig=x";

    @Test
    @DisplayName("OCR 결과 있으면 OcrResult 반환 + download TTL 10분")
    void extractAndValidate_whenOcrReturnsItems_returnsResult() {
        // given
        given(fileStoragePort.issueDownloadUrl(eq(IMAGE_KEY), eq(Duration.ofMinutes(10))))
                .willReturn(DOWNLOAD_URL);
        OcrItem item = new OcrItem("KD-001", "타이레놀", "타이레놀정500mg",
                BigDecimal.ONE, "정", 3, 7, new BigDecimal("0.95"), null);
        given(ocrPort.extractFromImage(DOWNLOAD_URL))
                .willReturn(new OcrResult(List.of(item), "식약처"));

        // when
        OcrResult result = sut.extractAndValidate(IMAGE_KEY);

        // then
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).kdCode()).isEqualTo("KD-001");
        assertThat(result.items().get(0).durationDays()).isEqualTo(7);
        verify(fileStoragePort).issueDownloadUrl(IMAGE_KEY, Duration.ofMinutes(10));
        verify(ocrPort).extractFromImage(DOWNLOAD_URL);
    }

    @Test
    @DisplayName("OCR 결과 빈 리스트 → OCR_EMPTY 예외")
    void extractAndValidate_whenOcrReturnsEmpty_throwsOcrEmpty() {
        // given
        given(fileStoragePort.issueDownloadUrl(any(), any())).willReturn(DOWNLOAD_URL);
        given(ocrPort.extractFromImage(any())).willReturn(new OcrResult(List.of(), "unknown"));

        // when / then
        assertThatThrownBy(() -> sut.extractAndValidate(IMAGE_KEY))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OCR_EMPTY);
    }
}
