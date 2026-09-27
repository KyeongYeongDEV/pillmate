package com.pillmate.notification.application;

import com.pillmate.common.monitoring.SlackNotifier;
import com.pillmate.notification.application.port.NotificationSenderPort;
import com.pillmate.notification.application.port.NotificationSenderPort.NotificationCommand;
import com.pillmate.notification.domain.model.Notification;
import com.pillmate.notification.domain.model.NotificationStatus;
import com.pillmate.notification.domain.model.NotificationType;
import com.pillmate.notification.domain.repository.NotificationRepository;
import com.pillmate.user.domain.model.User;
import com.pillmate.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Polling Outbox 재시도 스위퍼(2026-09-27) — 브로커 없이 기존 notifications 테이블을 outbox 로
// 재활용한다. DOSE_REMINDER/DOSE_OVERDUE 는 발송이 PENDING 에 방치되면(FCM 실패·앱 크래시 등)
// 최초 발송 폴러가 다신 안 보므로, 이 스위퍼가 별도로 찾아 재시도한다. 소셜성 알림(칭찬·넛지)은
// best-effort 로 충분해 대상에서 제외 — 안전 관련(복약 리마인더류)에만 at-least-once 를 보장한다.
@Slf4j
@Service
@RequiredArgsConstructor
public class RetryStuckNotificationsService {

    private static final List<NotificationType> RETRIABLE_TYPES =
            List.of(NotificationType.DOSE_REMINDER, NotificationType.DOSE_OVERDUE);
    private static final Duration STUCK_THRESHOLD = Duration.ofMinutes(2);
    private static final int MAX_RETRIES = 3;
    private static final String ROUTE_HOME = "/home";
    private static final String DATA_KEY_CHANNEL = "channel";
    private static final String CHANNEL_DOSE_REMINDER = "dose-reminder";

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final NotificationSenderPort notificationSenderPort;
    private final NotificationPersistenceService notificationPersistenceService;
    private final SlackNotifier slackNotifier;
    private final Clock clock;

    public int retryStuck() {
        Instant before = Instant.now(clock).minus(STUCK_THRESHOLD);
        List<Notification> stuck = notificationRepository.findStuckPendingByTypesBefore(
                NotificationStatus.PENDING, RETRIABLE_TYPES, before, MAX_RETRIES);
        if (stuck.isEmpty()) {
            return 0;
        }
        dispatchRetry(stuck);
        return stuck.size();
    }

    private void dispatchRetry(List<Notification> stuck) {
        Map<Long, String> tokensByUserId = tokensByUserId(stuck);
        List<NotificationCommand> commands = stuck.stream()
                .map(n -> toCommand(n, tokensByUserId.get(n.getRecipientUserId())))
                .toList();
        List<Long> sentIds = notificationSenderPort.sendAll(commands);
        if (!sentIds.isEmpty()) {
            notificationPersistenceService.markSentAll(sentIds, Instant.now(clock));
        }
        handleFailedAttempts(stuck, Set.copyOf(sentIds));
    }

    private void handleFailedAttempts(List<Notification> stuck, Set<Long> sentIds) {
        List<Notification> failed = stuck.stream().filter(n -> !sentIds.contains(n.getId())).toList();
        if (failed.isEmpty()) {
            return;
        }
        List<Long> failedIds = failed.stream().map(Notification::getId).toList();
        notificationRepository.incrementRetryCountByIdIn(failedIds);
        markExhaustedAndAlert(failed);
    }

    // retryCount 는 "이번 시도 전" 값 — +1 이 한도에 도달하면 이번이 마지막 시도였다는 뜻.
    private void markExhaustedAndAlert(List<Notification> failed) {
        List<Long> exhaustedIds = failed.stream()
                .filter(n -> n.getRetryCount() + 1 >= MAX_RETRIES)
                .map(Notification::getId)
                .toList();
        if (exhaustedIds.isEmpty()) {
            return;
        }
        notificationRepository.markFailedByIdIn(exhaustedIds, NotificationStatus.FAILED);
        log.warn("복약 알림 재시도 한도 초과 notificationIds={}", exhaustedIds);
        slackNotifier.send(String.format(
                "⚠️ 복약 알림 %d건이 %d회 재시도 후에도 발송 실패 (FAILED 전환) notificationIds=%s",
                exhaustedIds.size(), MAX_RETRIES, exhaustedIds));
    }

    private Map<Long, String> tokensByUserId(List<Notification> notifications) {
        List<Long> recipientIds = notifications.stream()
                .map(Notification::getRecipientUserId)
                .distinct()
                .toList();
        Map<Long, String> tokens = new HashMap<>();
        userRepository.findAllByIdIn(recipientIds)
                .forEach(u -> tokens.put(u.getId(), u.getExpoPushToken()));
        return tokens;
    }

    private NotificationCommand toCommand(Notification n, String token) {
        Map<String, String> data = new HashMap<>();
        data.put("route", ROUTE_HOME);
        data.put("type", n.getType().name());
        data.put("notificationId", String.valueOf(n.getId()));
        if (n.getRecipientUserId().equals(n.getActorUserId())) {
            data.put(DATA_KEY_CHANNEL, CHANNEL_DOSE_REMINDER);
        }
        return new NotificationCommand(n.getId(), n.getRecipientUserId(), token, n.getTitle(), n.getBody(), data);
    }
}
