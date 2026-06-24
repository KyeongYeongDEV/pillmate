package com.pillmate.notification.infrastructure.fcm;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.pillmate.notification.application.port.NotificationSenderPort.NotificationCommand;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FcmSenderAdapterMetricsTest {

    private SimpleMeterRegistry registry;
    private FirebaseMessagingProvider messagingProvider;
    private FcmSenderAdapter adapter;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        messagingProvider = mock(FirebaseMessagingProvider.class);
        adapter = new FcmSenderAdapter(messagingProvider, registry);
    }

    @Test
    @DisplayName("FCM 발송 성공 시 sent 카운터 증가")
    void send_success_incrementsSentCounter() throws FirebaseMessagingException {
        FirebaseMessaging messaging = mock(FirebaseMessaging.class);
        when(messagingProvider.get()).thenReturn(Optional.of(messaging));
        when(messaging.send(any())).thenReturn("msg-id-123");

        adapter.send(commandWithToken("ExponentPushToken[abc]"));

        assertThat(sentCount()).isEqualTo(1.0);
        assertThat(failedCount()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("FCM 발송 실패 시 failed 카운터 증가")
    void send_failure_incrementsFailedCounter() throws FirebaseMessagingException {
        FirebaseMessaging messaging = mock(FirebaseMessaging.class);
        when(messagingProvider.get()).thenReturn(Optional.of(messaging));
        FirebaseMessagingException fme = mock(FirebaseMessagingException.class);
        when(messaging.send(any())).thenThrow(fme);

        adapter.send(commandWithToken("ExponentPushToken[abc]"));

        assertThat(failedCount()).isEqualTo(1.0);
        assertThat(sentCount()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("토큰 없으면 sent/failed 카운터 모두 변화 없음")
    void send_noToken_noCounterChange() {
        adapter.send(commandWithToken(null));

        assertThat(sentCount()).isEqualTo(0.0);
        assertThat(failedCount()).isEqualTo(0.0);
    }

    private NotificationCommand commandWithToken(String token) {
        return new NotificationCommand(1L, 42L, token, "제목", "내용", null);
    }

    private double sentCount() {
        return registry.counter("pillmate.notifications.sent", "provider", "fcm").count();
    }

    private double failedCount() {
        return registry.counter("pillmate.notifications.failed", "provider", "fcm").count();
    }
}
