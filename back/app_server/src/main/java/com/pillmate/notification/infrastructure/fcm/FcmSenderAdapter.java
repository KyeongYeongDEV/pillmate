package com.pillmate.notification.infrastructure.fcm;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.pillmate.notification.application.port.NotificationSenderPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * firebase-admin 기반 직접 FCM 발송 어댑터.
 * provider=fcm 일 때만 활성. 자격증명 미준비/토큰 누락/발송 실패 전부 graceful skip (운영 안전).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "pillmate.notification.provider", havingValue = "fcm")
public class FcmSenderAdapter implements NotificationSenderPort {

    private final FirebaseMessagingProvider messagingProvider;
    private final Counter sentCounter;
    private final Counter failedCounter;

    public FcmSenderAdapter(FirebaseMessagingProvider messagingProvider, MeterRegistry registry) {
        this.messagingProvider = messagingProvider;
        this.sentCounter = Counter.builder("pillmate.notifications.sent")
                .tag("provider", "fcm")
                .description("FCM push notifications successfully sent")
                .register(registry);
        this.failedCounter = Counter.builder("pillmate.notifications.failed")
                .tag("provider", "fcm")
                .description("FCM push notifications failed to send")
                .register(registry);
    }

    @Override
    public void send(NotificationCommand command) {
        if (isBlankToken(command.recipientPushToken())) {
            log.info("[FCM] skip — no token recipient={}", command.recipientUserId());
            return;
        }

        Optional<FirebaseMessaging> messaging = messagingProvider.get();
        if (messaging.isEmpty()) {
            log.warn("[FCM] skip — FirebaseMessaging 미초기화 recipient={}", command.recipientUserId());
            return;
        }

        dispatch(messaging.get(), command);
    }

    private void dispatch(FirebaseMessaging messaging, NotificationCommand command) {
        try {
            String messageId = messaging.send(toMessage(command));
            sentCounter.increment();
            log.info("[FCM] sent recipient={} messageId={}", command.recipientUserId(), messageId);
        } catch (FirebaseMessagingException e) {
            failedCounter.increment();
            log.warn("[FCM] send 실패 recipient={} errorCode={} reason={}",
                    command.recipientUserId(), e.getMessagingErrorCode(), e.getMessage());
        }
    }

    private Message toMessage(NotificationCommand command) {
        return Message.builder()
                .setToken(command.recipientPushToken())
                .setNotification(Notification.builder()
                        .setTitle(command.title())
                        .setBody(command.body())
                        .build())
                .putAllData(command.data() == null ? Map.of() : command.data())
                .build();
    }

    private boolean isBlankToken(String token) {
        return token == null || token.isBlank();
    }
}
