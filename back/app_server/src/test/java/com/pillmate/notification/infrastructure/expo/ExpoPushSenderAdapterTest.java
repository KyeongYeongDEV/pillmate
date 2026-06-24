package com.pillmate.notification.infrastructure.expo;

import com.pillmate.notification.application.port.NotificationSenderPort.NotificationCommand;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;

import java.util.Map;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@RestClientTest(ExpoPushSenderAdapter.class)
@TestPropertySource(properties = {
        "pillmate.notification.provider=expo",
        "pillmate.notification.expo.base-url=https://exp.host"
})
@DisplayName("ExpoPushSenderAdapter — MockRestServiceServer 단위")
class ExpoPushSenderAdapterTest {

    @TestConfiguration
    static class MetricsConfig {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Autowired ExpoPushSenderAdapter adapter;
    @Autowired MockRestServiceServer server;

    @Test
    @DisplayName("토큰 있는 사용자 — POST exp.host /--/api/v2/push/send body 검증 + 200 정상")
    void send_whenTokenPresent_postsToExpo() {
        server.expect(requestTo("https://exp.host/--/api/v2/push/send"))
                .andExpect(jsonPath("$.to").value("ExponentPushToken[abc]"))
                .andExpect(jsonPath("$.title").value("복약 알림"))
                .andExpect(jsonPath("$.body").value("그룹 멤버가 복약을 완료했어요"))
                .andExpect(jsonPath("$.data.route").value("/group/42"))
                .andRespond(withSuccess("""
                        {"data":[{"status":"ok","id":"YYYY-ZZZZ"}]}
                        """, MediaType.APPLICATION_JSON));

        adapter.send(new NotificationCommand(
                1L, 2L, "ExponentPushToken[abc]",
                "복약 알림", "그룹 멤버가 복약을 완료했어요",
                Map.of("route", "/group/42")));

        server.verify();
    }

    @Test
    @DisplayName("토큰 없음 → POST 호출 0회 (skip)")
    void send_whenNoToken_skips() {
        adapter.send(new NotificationCommand(
                1L, 2L, null, "t", "b", Map.of()));
        server.verify(); // 0 expectations, must not have requests
    }

    @Test
    @DisplayName("422 응답 — exception 미발생 (로그만)")
    void send_when422_doesNotThrow() {
        server.expect(requestTo("https://exp.host/--/api/v2/push/send"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                              {"errors":[{"code":"DeviceNotRegistered","message":"token invalid"}]}
                              """));

        adapter.send(new NotificationCommand(
                1L, 2L, "ExponentPushToken[bad]",
                "t", "b", Map.of()));

        server.verify();
    }
}
