package com.pillmate.notification.infrastructure.fcm;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.pillmate.notification.application.port.NotificationSenderPort.NotificationCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("FcmSenderAdapter — firebase-admin 직접 발송")
class FcmSenderAdapterTest {

    @Mock FirebaseMessagingProvider messagingProvider;
    @Mock FirebaseMessaging firebaseMessaging;

    private FcmSenderAdapter adapter() {
        return new FcmSenderAdapter(messagingProvider);
    }

    private NotificationCommand command(String token) {
        return new NotificationCommand(
                1L, 7L, token, "복약 시간", "타이레놀 복용하세요",
                Map.of("route", "/group/10"));
    }

    @Test
    @DisplayName("정상 토큰 + FirebaseMessaging 존재 → send 1회 호출")
    void send_validTokenAndMessaging_dispatches() throws Exception {
        given(messagingProvider.get()).willReturn(Optional.of(firebaseMessaging));
        given(firebaseMessaging.send(any(Message.class))).willReturn("msg-id-1");

        adapter().send(command("fcm-token-abcdef"));

        verify(firebaseMessaging).send(any(Message.class));
    }

    @Test
    @DisplayName("토큰 null → skip (provider 미조회)")
    void send_nullToken_skips() {
        adapter().send(command(null));

        verify(messagingProvider, never()).get();
    }

    @Test
    @DisplayName("토큰 blank → skip (provider 미조회)")
    void send_blankToken_skips() {
        adapter().send(command("   "));

        verify(messagingProvider, never()).get();
    }

    @Test
    @DisplayName("자격증명 미초기화(Optional.empty) → graceful skip, 앱 안 죽음")
    void send_noFirebaseMessaging_gracefulSkip() {
        given(messagingProvider.get()).willReturn(Optional.empty());

        assertThatCode(() -> adapter().send(command("fcm-token-abcdef")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("FirebaseMessagingException 발생 → graceful 로그, 예외 전파 안 함")
    void send_firebaseException_gracefulLog() throws Exception {
        given(messagingProvider.get()).willReturn(Optional.of(firebaseMessaging));
        FirebaseMessagingException ex = mock(FirebaseMessagingException.class);
        given(firebaseMessaging.send(any(Message.class))).willThrow(ex);

        assertThatCode(() -> adapter().send(command("fcm-token-abcdef")))
                .doesNotThrowAnyException();
    }
}
