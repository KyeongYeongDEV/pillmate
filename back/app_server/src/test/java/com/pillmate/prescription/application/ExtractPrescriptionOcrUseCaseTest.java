package com.pillmate.prescription.application;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.prescription.application.dto.ExtractedDrugItem;
import com.pillmate.prescription.application.dto.OcrExtractResponse;
import com.pillmate.prescription.application.port.OcrPort.OcrItem;
import com.pillmate.prescription.application.port.OcrPort.OcrResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("ExtractPrescriptionOcrUseCase — OCR 추출 only, persist 금지")
@ExtendWith(MockitoExtension.class)
class ExtractPrescriptionOcrUseCaseTest {

    @Mock OcrExtractService ocrExtractService;
    @Mock RegisterPrescriptionService registerPrescriptionService;
    @InjectMocks ExtractPrescriptionOcrUseCase sut;

    private static final String IMAGE_KEY = "prescriptions/2026/06/uuid.jpg";

    @Test
    @DisplayName("OCR 결과를 ExtractedDrugItem 으로 매핑 — kdCode·durationDays 포함")
    void extract_mapsOcrItemsToExtractedDrugItems_withAllFields() {
        // given
        OcrItem matched = new OcrItem("KD-001", "타이레놀", "타이레놀정500mg",
                new BigDecimal("1"), "정", 3, 7, new BigDecimal("0.95"), null);
        OcrItem unmatched = new OcrItem(null, "동광나자티딘캡슐150mg", null,
                new BigDecimal("150"), "mg", 2, 14, new BigDecimal("0.72"), null);
        given(ocrExtractService.extractAndValidate(IMAGE_KEY))
                .willReturn(new OcrResult(List.of(matched, unmatched), "식약처"));

        // when
        OcrExtractResponse response = sut.extract(IMAGE_KEY);

        // then
        assertThat(response.items()).hasSize(2);

        ExtractedDrugItem first = response.items().get(0);
        assertThat(first.kdCode()).isEqualTo("KD-001");
        assertThat(first.nameRaw()).isEqualTo("타이레놀");
        assertThat(first.durationDays()).isEqualTo(7);
        assertThat(first.confidence()).isEqualByComparingTo(new BigDecimal("0.95"));

        ExtractedDrugItem second = response.items().get(1);
        assertThat(second.kdCode()).isNull();
        assertThat(second.nameRaw()).isEqualTo("동광나자티딘캡슐150mg");
        assertThat(second.durationDays()).isEqualTo(14);
    }

    @Test
    @DisplayName("OCR 결과 빈 리스트 → OCR_EMPTY 예외 전파")
    void extract_whenOcrEmpty_throwsOcrEmpty() {
        // given
        given(ocrExtractService.extractAndValidate(any()))
                .willThrow(new PillmateException(ErrorCode.OCR_EMPTY));

        // when / then
        assertThatThrownBy(() -> sut.extract(IMAGE_KEY))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OCR_EMPTY);
    }

    @Test
    @DisplayName("extract 성공 시 RegisterPrescriptionService(persist) 절대 호출 안 함")
    void extract_doesNotCallRegisterService_noPersist() {
        // given
        OcrItem item = new OcrItem("KD-001", "타이레놀", "타이레놀정500mg",
                BigDecimal.ONE, "정", 3, 7, new BigDecimal("0.95"), null);
        given(ocrExtractService.extractAndValidate(IMAGE_KEY))
                .willReturn(new OcrResult(List.of(item), "식약처"));

        // when
        sut.extract(IMAGE_KEY);

        // then — persist 계층 일절 호출 없음
        verify(ocrExtractService).extractAndValidate(IMAGE_KEY);
        verifyNoInteractions(registerPrescriptionService);
    }
}
