package com.pillmate.notification.infrastructure.scheduler;

import com.pillmate.common.monitoring.SlackNotifier;
import com.pillmate.notification.application.RetryStuckNotificationsService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

// Polling Outbox 재시도 스위퍼 폴러(2026-09-27) — 다른 폴러(10~30s)보다 느슨한 주기로 충분
// (스위퍼 대상 자체가 "이미 최초 발송을 놓친" 드문 케이스라 지연 허용).
@Slf4j
@Component
public class RetryStuckNotificationsPoller {

    private static final long POLL_INTERVAL_MS = 60_000;
    private static final int FAILURE_ALERT_THRESHOLD = 3;

    private final RetryStuckNotificationsService retryStuckNotifications;
    private final Counter successCounter;
    private final Counter failureCounter;
    private final SlackNotifier slackNotifier;
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);

    public RetryStuckNotificationsPoller(RetryStuckNotificationsService retryStuckNotifications,
                                         MeterRegistry registry,
                                         SlackNotifier slackNotifier) {
        this.retryStuckNotifications = retryStuckNotifications;
        this.slackNotifier = slackNotifier;
        this.successCounter = Counter.builder("pillmate.notification.retry.poller.runs")
                .tag("result", "success")
                .description("Stuck notification retry poller successful runs")
                .register(registry);
        this.failureCounter = Counter.builder("pillmate.notification.retry.poller.runs")
                .tag("result", "failure")
                .description("Stuck notification retry poller failed runs")
                .register(registry);
    }

    @Scheduled(fixedDelay = POLL_INTERVAL_MS)
    public void poll() {
        try {
            int retried = retryStuckNotifications.retryStuck();
            successCounter.increment();
            consecutiveFailures.set(0);
            if (retried > 0) {
                log.info("RetryStuckNotificationsPoller retried={}", retried);
            }
        } catch (RuntimeException ex) {
            failureCounter.increment();
            log.error("RetryStuckNotificationsPoller failed reason={}", ex.getMessage(), ex);
            int failures = consecutiveFailures.incrementAndGet();
            if (failures >= FAILURE_ALERT_THRESHOLD) {
                slackNotifier.send(String.format(
                        "⚠️ 알림 재시도 스위퍼 %d회 연속 실패 reason=%s", failures, ex.getClass().getSimpleName()));
                consecutiveFailures.set(0);
            }
        }
    }
}
