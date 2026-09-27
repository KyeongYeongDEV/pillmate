package com.pillmate.notification.application;

import com.pillmate.common.monitoring.SlackNotifier;
import com.pillmate.notification.application.port.NotificationSenderPort;
import com.pillmate.notification.application.port.NotificationSenderPort.NotificationCommand;
import com.pillmate.notification.domain.model.Notification;
import com.pillmate.notification.domain.model.NotificationStatus;
import com.pillmate.notification.domain.model.NotificationType;
import com.pillmate.notification.domain.repository.NotificationRepository;
import com.pillmate.user.domain.model.PushProvider;
import com.pillmate.user.domain.model.User;
import com.pillmate.user.domain.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 2026-09-27 Polling Outbox 재시도 스위퍼 — DOSE_REMINDER/DOSE_OVERDUE 처럼 "최소 1번은 가야 하는"
// 알림이 PENDING 에 방치되면(발송 실패·앱 크래시 등) 재시도한다. 브로커 없이 기존 notifications
// 테이블을 그대로 outbox 로 재활용 — Kafka 등 신규 인프라 도입 없음(no-overengineering).
@DisplayName("RetryStuckNotificationsService — 단위 테스트")
@ExtendWith(MockitoExtension.class)
class RetryStuckNotificationsServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-09-27T10:00:00Z");
    private static final Long RECIPIENT_ID = 1L;

    @Mock NotificationRepository notificationRepository;
    @Mock UserRepository userRepository;
    @Mock NotificationSenderPort notificationSenderPort;
    @Mock NotificationPersistenceService notificationPersistenceService;
    @Mock SlackNotifier slackNotifier;
    @Spy  Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    @InjectMocks RetryStuckNotificationsService sut;

    @Test
    @DisplayName("stuck 없으면 아무 것도 안 함")
    void retryStuck_whenNoneStuck_doesNothing() {
        given(notificationRepository.findStuckPendingByTypesBefore(any(), any(), any(), any(Integer.class)))
                .willReturn(List.of());

        int retried = sut.retryStuck();

        assertThat(retried).isZero();
        verify(notificationSenderPort, never()).sendAll(anyList());
    }

    @Test
    @DisplayName("stuck 알림 재발송 성공 — markSentAll 호출, retryCount 증가 없음")
    void retryStuck_whenResendSucceeds_marksSent() {
        Notification stuck = reminderPending(10L, RECIPIENT_ID);
        User recipient = userWithToken(RECIPIENT_ID, "ExponentPushToken[abc]");
        given(notificationRepository.findStuckPendingByTypesBefore(any(), any(), any(), any(Integer.class)))
                .willReturn(List.of(stuck));
        given(userRepository.findAllByIdIn(List.of(RECIPIENT_ID))).willReturn(List.of(recipient));
        given(notificationSenderPort.sendAll(anyList())).willReturn(List.of(10L));

        int retried = sut.retryStuck();

        assertThat(retried).isEqualTo(1);
        verify(notificationPersistenceService).markSentAll(List.of(10L), FIXED_NOW);
        verify(notificationRepository, never()).incrementRetryCountByIdIn(any());
    }

    @Test
    @DisplayName("재발송도 실패 + 한도 미달 — retryCount 증가만, FAILED 전환 없음")
    void retryStuck_whenResendFailsButUnderLimit_incrementsRetryCountOnly() {
        Notification stuck = reminderPending(10L, RECIPIENT_ID);
        ReflectionTestUtils.setField(stuck, "retryCount", 1);
        User recipient = userWithToken(RECIPIENT_ID, "ExponentPushToken[abc]");
        given(notificationRepository.findStuckPendingByTypesBefore(any(), any(), any(), any(Integer.class)))
                .willReturn(List.of(stuck));
        given(userRepository.findAllByIdIn(List.of(RECIPIENT_ID))).willReturn(List.of(recipient));
        given(notificationSenderPort.sendAll(anyList())).willReturn(List.of());

        sut.retryStuck();

        verify(notificationRepository).incrementRetryCountByIdIn(List.of(10L));
        verify(notificationRepository, never()).markFailedByIdIn(any(), any());
        verify(slackNotifier, never()).send(any());
    }

    @Test
    @DisplayName("재발송 실패 + 한도 도달(3회째) — FAILED 전환 + Slack 알림")
    void retryStuck_whenResendFailsAtLimit_marksFailedAndAlerts() {
        Notification stuck = reminderPending(10L, RECIPIENT_ID);
        ReflectionTestUtils.setField(stuck, "retryCount", 2); // 이번이 3번째 시도(0-indexed 2 → +1 = 3 = MAX)
        User recipient = userWithToken(RECIPIENT_ID, "ExponentPushToken[abc]");
        given(notificationRepository.findStuckPendingByTypesBefore(any(), any(), any(), any(Integer.class)))
                .willReturn(List.of(stuck));
        given(userRepository.findAllByIdIn(List.of(RECIPIENT_ID))).willReturn(List.of(recipient));
        given(notificationSenderPort.sendAll(anyList())).willReturn(List.of());

        sut.retryStuck();

        verify(notificationRepository).incrementRetryCountByIdIn(List.of(10L));
        verify(notificationRepository).markFailedByIdIn(eq(List.of(10L)), eq(NotificationStatus.FAILED));
        verify(slackNotifier).send(any());
    }

    @Test
    @DisplayName("조회 조건 — PENDING + DOSE_REMINDER/DOSE_OVERDUE 타입 + 2분 이전 + retryCount<3")
    void retryStuck_queriesWithCorrectFilters() {
        given(notificationRepository.findStuckPendingByTypesBefore(any(), any(), any(), any(Integer.class)))
                .willReturn(List.of());

        sut.retryStuck();

        verify(notificationRepository).findStuckPendingByTypesBefore(
                eq(NotificationStatus.PENDING),
                eq(List.of(NotificationType.DOSE_REMINDER, NotificationType.DOSE_OVERDUE)),
                eq(FIXED_NOW.minusSeconds(120)),
                eq(3));
    }

    private Notification reminderPending(Long id, Long recipientId) {
        Notification n = Notification.doseReminder(recipientId, null, 200L, "아침 약 드실 시간이에요");
        ReflectionTestUtils.setField(n, "id", id);
        return n;
    }

    private User userWithToken(Long id, String token) {
        User user = User.dummy("user-" + id);
        ReflectionTestUtils.setField(user, "id", id);
        user.registerPushToken(token, PushProvider.EXPO);
        return user;
    }
}
