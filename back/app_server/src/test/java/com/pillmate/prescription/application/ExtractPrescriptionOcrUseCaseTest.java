package com.pillmate.prescription.application;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.common.ratelimit.RateLimitExceededException;
import com.pillmate.common.ratelimit.RateLimiterPort;
import com.pillmate.prescription.application.dto.ExtractedDrugItem;
import com.pillmate.prescription.application.dto.OcrExtractResponse;
import com.pillmate.prescription.application.port.OcrPort.OcrItem;
import com.pillmate.prescription.application.port.OcrPort.OcrResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("ExtractPrescriptionOcrUseCase — OCR 추출 only, persist 금지 + 일일 rate limit")
@ExtendWith(MockitoExtension.class)
class ExtractPrescriptionOcrUseCaseTest {

    @Mock OcrExtractService ocrExtractService;
    @Mock RegisterPrescriptionService registerPrescriptionService;
    @Mock RateLimiterPort rateLimiterPort;
    @InjectMocks ExtractPrescriptionOcrUseCase sut;

    private static final String IMAGE_KEY = "prescriptions/2026/06/uuid.jpg";
    private static final Long USER_ID = 7L;
    private static final int DAILY_LIMIT = 50;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(sut, "rateLimitEnabled", true);
        ReflectionTestUtils.setField(sut, "ocrDailyLimit", DAILY_LIMIT);
    }

    @Test
    @DisplayName("OCR 결과를 ExtractedDrugItem 으로 매핑 — kdCode·durationDays·matchedName·imageUrl 포함")
    void extract_mapsOcrItemsToExtractedDrugItems_withAllFields() {
        // given
        OcrItem matched = new OcrItem("KD-001", "타이레놀", "타이레놀정500mg",
                new BigDecimal("1"), "정", 3, 7, new BigDecimal("0.95"),
                "https://cdn.example.com/drugs/KD-001.jpg");
        OcrItem unmatched = new OcrItem(null, "동광나자티딘캡슐150mg", null,
                new BigDecimal("150"), "mg", 2, 14, new BigDecimal("0.72"), null);
        given(ocrExtractService.extractAndValidate(IMAGE_KEY))
                .willReturn(new OcrResult(List.of(matched, unmatched), "식약처"));

        // when
        OcrExtractResponse response = sut.extract(IMAGE_KEY, USER_ID);

        // then
        assertThat(response.items()).hasSize(2);

        ExtractedDrugItem first = response.items().get(0);
        assertThat(first.kdCode()).isEqualTo("KD-001");
        assertThat(first.nameRaw()).isEqualTo("타이레놀");
        assertThat(first.matchedName()).isEqualTo("타이레놀정500mg");
        assertThat(first.imageUrl()).isEqualTo("https://cdn.example.com/drugs/KD-001.jpg");
        assertThat(first.durationDays()).isEqualTo(7);
        assertThat(first.confidence()).isEqualByComparingTo(new BigDecimal("0.95"));

        ExtractedDrugItem second = response.items().get(1);
        assertThat(second.kdCode()).isNull();
        assertThat(second.nameRaw()).isEqualTo("동광나자티딘캡슐150mg");
        assertThat(second.matchedName()).isNull();
        assertThat(second.imageUrl()).isNull();
        assertThat(second.durationDays()).isEqualTo(14);
    }

    @Test
    @DisplayName("AI 응답 piiDetected=true → 응답에 그대로 전파 (FE 등록 차단 플래그)")
    void extract_whenPiiDetected_propagatesFlag() {
        // given
        given(ocrExtractService.extractAndValidate(IMAGE_KEY))
                .willReturn(new OcrResult(List.of(item()), "식약처", true));

        // when
        OcrExtractResponse response = sut.extract(IMAGE_KEY, USER_ID);

        // then
        assertThat(response.piiDetected()).isTrue();
    }

    @Test
    @DisplayName("주민번호 미감지 → piiDetected=false (정상 등록 경로)")
    void extract_whenNoPii_flagFalse() {
        // given
        given(ocrExtractService.extractAndValidate(IMAGE_KEY))
                .willReturn(new OcrResult(List.of(item()), "식약처"));

        // when
        OcrExtractResponse response = sut.extract(IMAGE_KEY, USER_ID);

        // then
        assertThat(response.piiDetected()).isFalse();
    }

    @Test
    @DisplayName("OCR 결과 빈 리스트 → OCR_EMPTY 예외 전파")
    void extract_whenOcrEmpty_throwsOcrEmpty() {
        // given
        given(ocrExtractService.extractAndValidate(any()))
                .willThrow(new PillmateException(ErrorCode.OCR_EMPTY));

        // when / then
        assertThatThrownBy(() -> sut.extract(IMAGE_KEY, USER_ID))
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
        sut.extract(IMAGE_KEY, USER_ID);

        // then — persist 계층 일절 호출 없음
        verify(ocrExtractService).extractAndValidate(IMAGE_KEY);
        verifyNoInteractions(registerPrescriptionService);
    }

    // ─── T-BE-OCR-RATE-LIMIT: 사용자별 일일 한도 (크레딧 소진 방어) ───────────

    @Test
    @DisplayName("rate limit — 진입부에서 checkAndIncrement(userId, 'ocr', 한도) 호출")
    void extract_checksRateLimitBeforePipeline() {
        // given
        given(ocrExtractService.extractAndValidate(IMAGE_KEY))
                .willReturn(new OcrResult(List.of(item()), "식약처"));

        // when
        sut.extract(IMAGE_KEY, USER_ID);

        // then
        verify(rateLimiterPort).checkAndIncrement(USER_ID, "ocr", DAILY_LIMIT);
    }

    @Test
    @DisplayName("rate limit 초과 — RateLimitExceededException 전파 + OCR 파이프라인 미진입 (Gemini 비용 0)")
    void extract_whenLimitExceeded_throwsAndSkipsPipeline() {
        // given
        willThrow(new RateLimitExceededException())
                .given(rateLimiterPort).checkAndIncrement(anyLong(), anyString(), anyInt());

        // when / then
        assertThatThrownBy(() -> sut.extract(IMAGE_KEY, USER_ID))
                .isInstanceOf(RateLimitExceededException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RATE_LIMIT_EXCEEDED);
        verifyNoInteractions(ocrExtractService);
    }

    @Test
    @DisplayName("rate limit disabled(토글 off) — 카운트 없이 파이프라인 진행")
    void extract_whenDisabled_skipsRateLimiter() {
        // given
        ReflectionTestUtils.setField(sut, "rateLimitEnabled", false);
        given(ocrExtractService.extractAndValidate(IMAGE_KEY))
                .willReturn(new OcrResult(List.of(item()), "식약처"));

        // when
        sut.extract(IMAGE_KEY, USER_ID);

        // then
        verify(rateLimiterPort, never()).checkAndIncrement(anyLong(), anyString(), anyInt());
    }

    @Test
    @DisplayName("userId null (미인증 경계) — 카운트 skip, 파이프라인은 진행 (인증은 필터 책임)")
    void extract_whenUserIdNull_skipsRateLimiter() {
        // given
        given(ocrExtractService.extractAndValidate(IMAGE_KEY))
                .willReturn(new OcrResult(List.of(item()), "식약처"));

        // when
        sut.extract(IMAGE_KEY, null);

        // then
        verify(rateLimiterPort, never()).checkAndIncrement(anyLong(), anyString(), anyInt());
    }

    private OcrItem item() {
        return new OcrItem("KD-001", "타이레놀", "타이레놀정500mg",
                BigDecimal.ONE, "정", 3, 7, new BigDecimal("0.95"), null);
    }
}
