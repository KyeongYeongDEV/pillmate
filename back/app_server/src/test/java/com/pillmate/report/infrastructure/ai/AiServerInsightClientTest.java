package com.pillmate.report.infrastructure.ai;

import com.pillmate.common.config.AiServerProperties;
import com.pillmate.report.application.port.LlmInsightPort.InsightContext;
import com.pillmate.report.application.port.LlmInsightPort.InsightDraft;
import com.pillmate.report.domain.model.PeriodType;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AiServerInsightClient 는 자체 RestClient(요청 팩토리 직접 설정)를 구성하기 때문에
 * @RestClientTest/MockRestServiceServer 로 검증 불가 (Spring 관리 RestClient.Builder 를
 * 주입받지 않음 — 별건, 본 리팩토링 범위 밖). 대신 실제 로컬 HTTP 서버로
 * ai-server.base-url(AiServerProperties) 가 실제 호출에 반영되는지 검증한다.
 *
 * 회귀 방지 대상: 과거 pillmate.ai-server.base-url 하드코딩 기본값을 무시하고
 * 항상 http://ai-server:8001 로만 호출하던 버그(P1, 같은 서버인데 키가 갈림).
 */
class AiServerInsightClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("ai-server.base-url 프로퍼티로 주입된 호스트로 호출한다")
    void generate_usesAiServerBaseUrlProperty() throws IOException {
        String responseJson = """
                {
                  "insights": [
                    {
                      "type": "TREND",
                      "severity": "INFO",
                      "title": "복약 순응도 양호",
                      "description": "지난 7일간 순응도가 높습니다.",
                      "source": "식약처",
                      "confidence": 0.9
                    }
                  ]
                }
                """;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/analyze/health-report", exchange -> {
            byte[] body = responseJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        AiServerInsightClient client = new AiServerInsightClient(
                new AiServerProperties(baseUrl, new AiServerProperties.Timeout(5000, 170000)));

        List<InsightDraft> drafts = client.generate(new InsightContext(
                7L, PeriodType.WEEKLY, LocalDate.now().minusDays(6), LocalDate.now(),
                90, new BigDecimal("0.9"), List.of(), List.of()));

        assertThat(drafts).hasSize(1);
        assertThat(drafts.get(0).source()).isEqualTo("식약처");
    }
}
