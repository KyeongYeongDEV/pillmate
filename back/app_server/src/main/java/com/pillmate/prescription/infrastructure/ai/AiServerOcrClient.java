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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class AiServerOcrClient implements OcrPort {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public AiServerOcrClient(RestClient.Builder builder,
                              @Value("${ai-server.base-url}") String baseUrl,
                              ObjectMapper objectMapper) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
    }

    @Override
    public OcrResult extractFromImage(String imageUrl, String imageKey) {
        try {
            return restClient.post()
                    .uri("/api/v1/ocr/prescription")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new AiServerOcrRequest(imageUrl, imageKey))
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
                    .toOcrResult(objectMapper);
        } catch (ResourceAccessException e) {
            log.error("OCR server connection failed: {}", e.getMessage());
            throw new PillmateException(ErrorCode.OCR_UPSTREAM_TIMEOUT);
        }
    }

    private record AiServerOcrRequest(String image_url, String image_key) {}

    private record AiServerOcrResponse(List<AiServerOcrItem> items, String source) {
        public OcrResult toOcrResult(ObjectMapper mapper) {
            return new OcrResult(items.stream().map(item -> item.toOcrItem(mapper)).toList(), source);
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
            BigDecimal confidence,
            String decision,
            String decision_reason,
            List<Map<String, Object>> candidate_options
    ) {
        public OcrItem toOcrItem(ObjectMapper mapper) {
            String candidateOptionsJson = serializeOptions(mapper);
            String decisionType = decision != null ? decision : "AUTO";
            return new OcrItem(kd_code, name_raw, matched_name, dose_amount, dose_unit,
                    frequency, duration_days, confidence, null, decisionType, candidateOptionsJson);
        }

        private String serializeOptions(ObjectMapper mapper) {
            if (candidate_options == null || candidate_options.isEmpty()) return null;
            try {
                return mapper.writeValueAsString(candidate_options);
            } catch (JsonProcessingException e) {
                return null;
            }
        }
    }
}
