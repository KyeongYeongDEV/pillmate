package com.pillmate.common.monitoring;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * SlackNotifier 단위 테스트 — URL 없을 때 no-op.
 */
class SlackNotifierTest {

    @Test
    @DisplayName("SLACK_WEBHOOK_URL 빈값 → HTTP 전혀 호출하지 않음, 예외 없음")
    void send_emptyWebhookUrl_isNoOp() {
        SlackNotifier notifier = new SlackNotifier(RestClient.builder(), "");
        assertThatCode(() -> notifier.send("어떤 메시지라도")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("SLACK_WEBHOOK_URL 공백 → no-op")
    void send_blankWebhookUrl_isNoOp() {
        SlackNotifier notifier = new SlackNotifier(RestClient.builder(), "   ");
        assertThatCode(() -> notifier.send("test")).doesNotThrowAnyException();
    }
}
