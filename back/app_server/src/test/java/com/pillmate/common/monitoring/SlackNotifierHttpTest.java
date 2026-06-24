package com.pillmate.common.monitoring;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@RestClientTest(SlackNotifier.class)
@TestPropertySource(properties = "slack.webhook-url=https://hooks.slack.com/test-webhook")
@DisplayName("SlackNotifier — MockRestServiceServer 단위")
class SlackNotifierHttpTest {

    @Autowired SlackNotifier notifier;
    @Autowired MockRestServiceServer server;

    @Test
    @DisplayName("URL 있을 때 → JSON {text} POST 전송")
    void send_whenUrlConfigured_postsTextPayload() {
        server.expect(requestTo("https://hooks.slack.com/test-webhook"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.text").value("⚠️ dose_log 생성 0건 (날짜 2026-06-24)"))
                .andRespond(withSuccess());

        notifier.send("⚠️ dose_log 생성 0건 (날짜 2026-06-24)");

        server.verify();
    }

    @Test
    @DisplayName("Slack 서버 5xx → 예외 전파 없음(본업 보호)")
    void send_whenSlackFails_doesNotPropagate() {
        server.expect(requestTo("https://hooks.slack.com/test-webhook"))
                .andRespond(withServerError());

        assertThatCode(() -> notifier.send("any message")).doesNotThrowAnyException();
    }
}
