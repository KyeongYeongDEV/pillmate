package com.pillmate.prescription.application;

import com.pillmate.common.ratelimit.RateLimiterPort;
import com.pillmate.prescription.application.dto.ExtractedDrugItem;
import com.pillmate.prescription.application.dto.OcrExtractResponse;
import com.pillmate.prescription.application.port.OcrPort.OcrItem;
import com.pillmate.prescription.application.port.OcrPort.OcrResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ExtractPrescriptionOcrUseCase {

    private static final String RATE_LIMIT_ACTION = "ocr";

    private final OcrExtractService ocrExtractService;
    private final RateLimiterPort rateLimiterPort;

    @Value("${pillmate.ratelimit.enabled:true}")
    private boolean rateLimitEnabled;

    @Value("${pillmate.ratelimit.ocr.daily-limit:50}")
    private int ocrDailyLimit;

    public OcrExtractResponse extract(String imageKey, Long userId) {
        enforceDailyLimit(userId);
        OcrResult result = ocrExtractService.extractAndValidate(imageKey);
        List<ExtractedDrugItem> items = result.items().stream()
                .map(this::toExtractedDrugItem)
                .toList();
        return new OcrExtractResponse(items, result.piiDetected());
    }

    // 캐시 hit 여부와 무관하게 호출 시도 자체를 카운트 — 이미지 변조로 sha256 캐시 우회하는 cost DoS 방어
    private void enforceDailyLimit(Long userId) {
        if (!rateLimitEnabled || userId == null) {
            return;
        }
        rateLimiterPort.checkAndIncrement(userId, RATE_LIMIT_ACTION, ocrDailyLimit);
    }

    private ExtractedDrugItem toExtractedDrugItem(OcrItem item) {
        return new ExtractedDrugItem(
                item.kdCode(),
                item.nameRaw(),
                item.matchedName(),
                item.doseAmount(),
                item.doseUnit(),
                item.frequency(),
                item.durationDays(),
                item.confidence(),
                item.imageUrl()
        );
    }
}
