package com.pillmate.common.monitoring;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 운영 신호를 Slack webhook 으로 전송하는 경량 컴포넌트.
 *
 * - SLACK_WEBHOOK_URL 빈값 → 즉시 return(로컬 OFF)
 * - 전송 실패해도 본업 막지 않음 (try-catch 경고 로그만)
 * - ★메시지에 환자정보·처방내용·토큰 금지 — 호출부가 책임
 */
@Slf4j
@Component
public class SlackNotifier {

    private final String webhookUrl;
    private final RestClient restClient;

    public SlackNotifier(RestClient.Builder builder,
                         @Value("${slack.webhook-url:}") String webhookUrl) {
        this.webhookUrl = webhookUrl;
        this.restClient = builder.build();
    }

    public void send(String text) {
        if (webhookUrl.isBlank()) {
            return;
        }
        try {
            restClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("text", text))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("[Slack] 알림 전송 실패: {}", e.getMessage());
        }
    }
}
