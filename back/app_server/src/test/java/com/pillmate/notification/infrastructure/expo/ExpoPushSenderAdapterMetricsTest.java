package com.pillmate.notification.infrastructure.expo;

import com.pillmate.notification.application.port.NotificationSenderPort.NotificationCommand;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExpoPushSenderAdapterMetricsTest {

    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
    }

    @Test
    @DisplayName("토큰 없으면 sent/failed 카운터 모두 변화 없음")
    void send_noToken_noCounterChange() {
        ExpoPushSenderAdapter adapter = buildAdapter();

        adapter.send(commandWithToken(null));

        assertThat(sentCount()).isEqualTo(0.0);
        assertThat(failedCount()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("어댑터 생성 시 sent/failed 카운터가 MeterRegistry에 등록된다")
    void constructor_registersCountersInRegistry() {
        buildAdapter();

        assertThat(registry.find("pillmate.notifications.sent").tag("provider", "expo").counter())
                .isNotNull();
        assertThat(registry.find("pillmate.notifications.failed").tag("provider", "expo").counter())
                .isNotNull();
    }

    private ExpoPushSenderAdapter buildAdapter() {
        return new ExpoPushSenderAdapter(
                RestClient.builder(),
                "https://exp.host",
                registry
        );
    }

    private NotificationCommand commandWithToken(String token) {
        return new NotificationCommand(1L, 42L, token, "제목", "내용", null);
    }

    private double sentCount() {
        return registry.counter("pillmate.notifications.sent", "provider", "expo").count();
    }

    private double failedCount() {
        return registry.counter("pillmate.notifications.failed", "provider", "expo").count();
    }
}
