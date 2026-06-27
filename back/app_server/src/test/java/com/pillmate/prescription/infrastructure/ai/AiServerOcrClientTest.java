package com.pillmate.prescription.infrastructure.ai;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.prescription.application.port.OcrPort.OcrItem;
import com.pillmate.prescription.application.port.OcrPort.OcrResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import org.springframework.test.context.TestPropertySource;

@RestClientTest(AiServerOcrClient.class)
@TestPropertySource(properties = {
    "ai-server.base-url=http://ai-server:8001"
})
class AiServerOcrClientTest {

    @Autowired
    private AiServerOcrClient client;

    @Autowired
    private MockRestServiceServer server;

    @Test
    @DisplayName("AI 서버가 200 OK를 반환하면 OcrResult로 매핑한다")
    void extractFromImage_whenAiServerReturns200_mapsToOcrResult() {
        // given
        String imageUrl = "http://s3/image.jpg";
        String responseJson = """
            {
              "items": [
                {
                  "kd_code": "195700020",
                  "name_raw": "활명수",
                  "matched_name": "활명수",
                  "dose_amount": 1.0,
                  "dose_unit": "병",
                  "frequency": 3,
                  "duration_days": 3,
                  "confidence": 0.95
                }
              ],
              "source": "식약처"
            }
            """;

        server.expect(requestTo("http://ai-server:8001/api/v1/ocr/prescription"))
                .andExpect(jsonPath("$.image_url").value(imageUrl))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        // when
        OcrResult result = client.extractFromImage(imageUrl, "prescriptions/uuid.jpg");

        // then
        assertThat(result.items()).hasSize(1);
        OcrItem item = result.items().get(0);
        assertThat(item.kdCode()).isEqualTo("195700020");
        assertThat(item.confidence()).isEqualTo(new BigDecimal("0.95"));
    }

    @Test
    @DisplayName("AI 서버가 504 Timeout을 반환하면 OCR_UPSTREAM_TIMEOUT 예외를 던진다")
    void extractFromImage_whenAiServerReturns504_throwsTimeout() {
        server.expect(requestTo("http://ai-server:8001/api/v1/ocr/prescription"))
                .andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT));

        assertThatThrownBy(() -> client.extractFromImage("http://url", null))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OCR_UPSTREAM_TIMEOUT);
    }

    @Test
    @DisplayName("AI 서버가 400을 반환하면 OCR_REQUEST_INVALID 예외를 던진다")
    void extractFromImage_whenAiServerReturns400_throwsRequestInvalid() {
        server.expect(requestTo("http://ai-server:8001/api/v1/ocr/prescription"))
                .andRespond(withBadRequest());

        assertThatThrownBy(() -> client.extractFromImage("http://url", null))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OCR_REQUEST_INVALID);
    }

    @Test
    @DisplayName("AI 서버가 500을 반환하면 OCR_UPSTREAM_FAILED 예외를 던진다")
    void extractFromImage_whenAiServerReturns500_throwsUpstreamFailed() {
        server.expect(requestTo("http://ai-server:8001/api/v1/ocr/prescription"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.extractFromImage("http://url", null))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OCR_UPSTREAM_FAILED);
    }

    @Test
    @DisplayName("소켓 read timeout 발생 시 OCR_UPSTREAM_TIMEOUT 예외를 던진다")
    void extractFromImage_whenSocketTimeout_throwsTimeout() {
        server.expect(requestTo("http://ai-server:8001/api/v1/ocr/prescription"))
                .andRespond(withException(new java.net.SocketTimeoutException("read timed out")));

        assertThatThrownBy(() -> client.extractFromImage("http://url", null))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OCR_UPSTREAM_TIMEOUT);
    }
}
