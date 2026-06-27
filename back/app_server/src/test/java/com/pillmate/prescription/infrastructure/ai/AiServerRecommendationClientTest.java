package com.pillmate.prescription.infrastructure.ai;

import com.pillmate.prescription.application.port.PrescriptionRecommendationPort.DrugContext;
import com.pillmate.prescription.application.port.PrescriptionRecommendationPort.InsightDraft;
import com.pillmate.prescription.domain.model.PrescriptionInsightType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@RestClientTest(AiServerRecommendationClient.class)
@TestPropertySource(properties = {
        "ai-server.base-url=http://ai-server:8001"
})
class AiServerRecommendationClientTest {

    @Autowired
    private AiServerRecommendationClient client;

    @Autowired
    private MockRestServiceServer server;

    private List<DrugContext> drugs() {
        return List.of(new DrugContext("200500823", "메트포르민정500밀리그램",
                new BigDecimal("1.00"), "정", 2, 30));
    }

    @Test
    @DisplayName("200 OK — insight 목록으로 매핑")
    void generate_returns200_mapsToDrafts() {
        String responseJson = """
            {
              "insights": [
                {
                  "type": "RECOMMENDATION",
                  "severity": "INFO",
                  "title": "비타민 B12 영향 가능",
                  "description": "장기 복용 시 흡수에 영향을 줄 수 있어요.",
                  "source": "식약처",
                  "confidence": 0.9
                }
              ]
            }
            """;
        server.expect(requestTo("http://ai-server:8001/api/v1/analyze/prescription-recommendation"))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        List<InsightDraft> drafts = client.generate(42L, 7L, drugs());

        assertThat(drafts).hasSize(1);
        assertThat(drafts.get(0).type()).isEqualTo(PrescriptionInsightType.RECOMMENDATION);
        assertThat(drafts.get(0).source()).isEqualTo("식약처");
    }

    @Test
    @DisplayName("출처 없는 insight 는 drop")
    void generate_dropsInsightWithoutSource() {
        String responseJson = """
            {
              "insights": [
                {
                  "type": "WARNING",
                  "severity": "WARN",
                  "title": "출처 없음",
                  "description": "근거 없음",
                  "source": "",
                  "confidence": 0.95
                }
              ]
            }
            """;
        server.expect(requestTo("http://ai-server:8001/api/v1/analyze/prescription-recommendation"))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        List<InsightDraft> drafts = client.generate(42L, 7L, drugs());

        assertThat(drafts).isEmpty();
    }

    @Test
    @DisplayName("5xx — graceful: 빈 리스트 반환 (등록 흐름 차단 X)")
    void generate_serverError_returnsEmptyList() {
        server.expect(requestTo("http://ai-server:8001/api/v1/analyze/prescription-recommendation"))
                .andRespond(withServerError());

        List<InsightDraft> drafts = client.generate(42L, 7L, drugs());

        assertThat(drafts).isEmpty();
    }

    @Test
    @DisplayName("503 Service Unavailable — graceful 빈 리스트")
    void generate_serviceUnavailable_returnsEmptyList() {
        server.expect(requestTo("http://ai-server:8001/api/v1/analyze/prescription-recommendation"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        List<InsightDraft> drafts = client.generate(42L, 7L, drugs());

        assertThat(drafts).isEmpty();
    }

    @Test
    @DisplayName("미매칭 약(code/name/doseUnit null) — 빈문자열로 coalesce 전송 (ai_server 422 회피)")
    void generate_nullCodeFields_sentAsEmptyString() {
        server.expect(requestTo("http://ai-server:8001/api/v1/analyze/prescription-recommendation"))
                .andExpect(jsonPath("$.drugs[0].code").value(""))
                .andExpect(jsonPath("$.drugs[0].name").value(""))
                .andExpect(jsonPath("$.drugs[0].dose_unit").value(""))
                .andRespond(withSuccess("{\"insights\":[]}", MediaType.APPLICATION_JSON));

        List<DrugContext> unmatched = List.of(
                new DrugContext(null, null, null, null, 3, 7));

        List<InsightDraft> drafts = client.generate(42L, 7L, unmatched);

        assertThat(drafts).isEmpty();
        server.verify();
    }
}
