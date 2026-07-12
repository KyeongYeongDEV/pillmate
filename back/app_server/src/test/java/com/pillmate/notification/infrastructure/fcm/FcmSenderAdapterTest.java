package com.pillmate.notification.infrastructure.fcm;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.SendResponse;
import com.pillmate.notification.application.port.NotificationSenderPort.NotificationCommand;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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
        return new FcmSenderAdapter(messagingProvider, new SimpleMeterRegistry());
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

    // ─── T-BE-NOTIFICATION-BATCH: sendAll(sendEach 배치) ─────────────────────

    private NotificationCommand command(Long notificationId, String token) {
        return new NotificationCommand(
                notificationId, notificationId + 100, token, "복약 시간", "타이레놀 복용하세요",
                Map.of("route", "/group/10"));
    }

    private BatchResponse batchResponseOf(boolean... successes) {
        BatchResponse batch = mock(BatchResponse.class);
        List<SendResponse> responses = new java.util.ArrayList<>();
        for (boolean success : successes) {
            SendResponse r = mock(SendResponse.class);
            given(r.isSuccessful()).willReturn(success);
            if (!success) {
                given(r.getException()).willReturn(mock(FirebaseMessagingException.class));
            }
            responses.add(r);
        }
        given(batch.getResponses()).willReturn(responses);
        return batch;
    }

    @Test
    @DisplayName("sendAll — 3건 정상 토큰 → sendEach 1회, 성공 notificationId 3개 반환")
    void sendAll_threeValidTokens_singleSendEachReturnsAllIds() throws Exception {
        given(messagingProvider.get()).willReturn(Optional.of(firebaseMessaging));
        BatchResponse batch = batchResponseOf(true, true, true);
        given(firebaseMessaging.sendEach(anyList())).willReturn(batch);

        List<Long> sent = adapter().sendAll(List.of(
                command(1L, "tok-1"), command(2L, "tok-2"), command(3L, "tok-3")));

        ArgumentCaptor<List<Message>> captor = ArgumentCaptor.forClass(List.class);
        verify(firebaseMessaging).sendEach(captor.capture());
        assertThat(captor.getValue()).hasSize(3);
        assertThat(sent).containsExactly(1L, 2L, 3L);
    }

    @Test
    @DisplayName("sendAll — 토큰 없는 command 는 배치에서 제외, 나머지만 발송")
    void sendAll_tokenlessExcluded_restSent() throws Exception {
        given(messagingProvider.get()).willReturn(Optional.of(firebaseMessaging));
        BatchResponse batch = batchResponseOf(true);
        given(firebaseMessaging.sendEach(anyList())).willReturn(batch);

        List<Long> sent = adapter().sendAll(List.of(
                command(1L, null), command(2L, "tok-2"), command(3L, "  ")));

        ArgumentCaptor<List<Message>> captor = ArgumentCaptor.forClass(List.class);
        verify(firebaseMessaging).sendEach(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(sent).containsExactly(2L);
    }

    @Test
    @DisplayName("sendAll — per-message 일부 실패 → 성공분만 반환 (전체 중단 X)")
    void sendAll_partialFailure_returnsOnlySuccesses() throws Exception {
        given(messagingProvider.get()).willReturn(Optional.of(firebaseMessaging));
        BatchResponse batch = batchResponseOf(true, false, true);
        given(firebaseMessaging.sendEach(anyList())).willReturn(batch);

        List<Long> sent = adapter().sendAll(List.of(
                command(1L, "tok-1"), command(2L, "tok-2"), command(3L, "tok-3")));

        assertThat(sent).containsExactly(1L, 3L);
    }

    @Test
    @DisplayName("sendAll — 전부 토큰 없음 → provider 미조회 + 빈 결과")
    void sendAll_allTokenless_skipsProvider() {
        List<Long> sent = adapter().sendAll(List.of(command(1L, null), command(2L, "")));

        verify(messagingProvider, never()).get();
        assertThat(sent).isEmpty();
    }

    @Test
    @DisplayName("sendAll — FirebaseMessaging 미초기화 → graceful skip, 빈 결과")
    void sendAll_noFirebaseMessaging_gracefulSkip() {
        given(messagingProvider.get()).willReturn(Optional.empty());

        List<Long> sent = adapter().sendAll(List.of(command(1L, "tok-1")));

        assertThat(sent).isEmpty();
    }

    @Test
    @DisplayName("sendAll — sendEach 자체가 예외 → graceful, 빈 결과 (앱 안 죽음)")
    void sendAll_sendEachThrows_gracefulEmpty() throws Exception {
        given(messagingProvider.get()).willReturn(Optional.of(firebaseMessaging));
        given(firebaseMessaging.sendEach(anyList()))
                .willThrow(mock(FirebaseMessagingException.class));

        assertThatCode(() -> {
            List<Long> sent = adapter().sendAll(List.of(command(1L, "tok-1")));
            assertThat(sent).isEmpty();
        }).doesNotThrowAnyException();
    }
}
