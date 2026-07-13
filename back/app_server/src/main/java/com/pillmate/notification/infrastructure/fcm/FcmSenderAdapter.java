package com.pillmate.notification.infrastructure.fcm;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import com.pillmate.notification.application.port.NotificationSenderPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
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

    private static final int FCM_BATCH_LIMIT = 500;
    // FE NotificationsBootstrap 채널 ID 와 문자열 일치 (FE-Dev 합의)
    private static final String CHANNEL_DOSE_REMINDER = "dose-reminder";
    private static final String CHANNEL_GROUP_ACTIVITY = "group-activity";
    private static final String DATA_KEY_TYPE = "type";
    private static final String TYPE_DOSE_REMINDER = "DOSE_REMINDER";

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

    @Override
    public List<Long> sendAll(List<NotificationCommand> commands) {
        List<NotificationCommand> sendable = commands.stream()
                .filter(c -> !isBlankToken(c.recipientPushToken()))
                .toList();
        if (sendable.isEmpty()) {
            return List.of();
        }
        Optional<FirebaseMessaging> messaging = messagingProvider.get();
        if (messaging.isEmpty()) {
            log.warn("[FCM] batch skip — FirebaseMessaging 미초기화 count={}", sendable.size());
            return List.of();
        }
        return dispatchChunks(messaging.get(), sendable);
    }

    private List<Long> dispatchChunks(FirebaseMessaging messaging, List<NotificationCommand> sendable) {
        List<Long> sentIds = new ArrayList<>();
        for (int from = 0; from < sendable.size(); from += FCM_BATCH_LIMIT) {
            int to = Math.min(from + FCM_BATCH_LIMIT, sendable.size());
            sentIds.addAll(dispatchBatch(messaging, sendable.subList(from, to)));
        }
        return sentIds;
    }

    private List<Long> dispatchBatch(FirebaseMessaging messaging, List<NotificationCommand> chunk) {
        try {
            BatchResponse response = messaging.sendEach(chunk.stream().map(this::toMessage).toList());
            return collectBatchResults(response, chunk);
        } catch (FirebaseMessagingException e) {
            failedCounter.increment(chunk.size());
            log.warn("[FCM] batch send 실패 count={} errorCode={} reason={}",
                    chunk.size(), e.getMessagingErrorCode(), e.getMessage());
            return List.of();
        }
    }

    private List<Long> collectBatchResults(BatchResponse response, List<NotificationCommand> chunk) {
        List<Long> sentIds = new ArrayList<>();
        List<SendResponse> responses = response.getResponses();
        for (int i = 0; i < responses.size(); i++) {
            if (responses.get(i).isSuccessful()) {
                sentCounter.increment();
                sentIds.add(chunk.get(i).notificationId());
                log.info("[FCM] sent recipient={} messageId={}",
                        chunk.get(i).recipientUserId(), responses.get(i).getMessageId());
            } else {
                failedCounter.increment();
                logBatchItemFailure(chunk.get(i), responses.get(i));
            }
        }
        return sentIds;
    }

    private void logBatchItemFailure(NotificationCommand command, SendResponse response) {
        FirebaseMessagingException e = response.getException();
        log.warn("[FCM] batch item 실패 recipient={} errorCode={} reason={}",
                command.recipientUserId(),
                e != null ? e.getMessagingErrorCode() : null,
                e != null ? e.getMessage() : "unknown");
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
                .setAndroidConfig(buildAndroidConfig(command))
                .putAllData(command.data() == null ? Map.of() : command.data())
                .build();
    }

    // 킬드 상태 전달 보장(priority HIGH) + FE 채널 분리(리마인더/그룹활동)
    private AndroidConfig buildAndroidConfig(NotificationCommand command) {
        return AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)
                .setNotification(AndroidNotification.builder()
                        .setChannelId(resolveChannelId(command.data()))
                        .build())
                .build();
    }

    private String resolveChannelId(Map<String, String> data) {
        String type = data == null ? null : data.get(DATA_KEY_TYPE);
        return TYPE_DOSE_REMINDER.equals(type) ? CHANNEL_DOSE_REMINDER : CHANNEL_GROUP_ACTIVITY;
    }

    private boolean isBlankToken(String token) {
        return token == null || token.isBlank();
    }
}
