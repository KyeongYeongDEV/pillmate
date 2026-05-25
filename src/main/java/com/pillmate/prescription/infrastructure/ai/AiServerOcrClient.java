package com.pillmate.prescription.infrastructure.ai;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.prescription.application.port.OcrPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Component
public class AiServerOcrClient implements OcrPort {

    private final RestClient restClient;

    public AiServerOcrClient(RestClient.Builder builder, @Value("${ai-server.base-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    @Override
    public OcrResult extractFromImage(String imageUrl) {
        try {
            return restClient.post()
                    .uri("/api/v1/ocr/prescription")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new AiServerOcrRequest(imageUrl))
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                        throw new PillmateException(ErrorCode.OCR_REQUEST_INVALID);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
                        if (response.getStatusCode().value() == 504) {
                            throw new PillmateException(ErrorCode.OCR_UPSTREAM_TIMEOUT);
                        }
                        throw new PillmateException(ErrorCode.OCR_UPSTREAM_FAILED);
                    })
                    .body(AiServerOcrResponse.class)
                    .toOcrResult();
        } catch (ResourceAccessException e) {
            log.error("OCR server connection failed: {}", e.getMessage());
            throw new PillmateException(ErrorCode.OCR_UPSTREAM_TIMEOUT);
        }
    }

    private record AiServerOcrRequest(String image_url) {}

    private record AiServerOcrResponse(List<AiServerOcrItem> items, String source) {
        public OcrResult toOcrResult() {
            return new OcrResult(items.stream().map(AiServerOcrItem::toOcrItem).toList(), source);
        }
    }

    private record AiServerOcrItem(
            String kd_code,
            String name_raw,
            String matched_name,
            BigDecimal dose_amount,
            String dose_unit,
            Integer frequency,
            Integer duration_days,
            BigDecimal confidence
    ) {
        public OcrItem toOcrItem() {
            return new OcrItem(kd_code, name_raw, matched_name, dose_amount, dose_unit, frequency, duration_days, confidence, null);
        }
    }
}
