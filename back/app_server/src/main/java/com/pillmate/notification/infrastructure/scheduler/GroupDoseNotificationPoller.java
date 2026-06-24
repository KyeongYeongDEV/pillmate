package com.pillmate.notification.infrastructure.scheduler;

import com.pillmate.common.monitoring.SlackNotifier;
import com.pillmate.notification.application.NotifyDueGroupDosesUseCase;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class GroupDoseNotificationPoller {

    private static final long POLL_INTERVAL_MS = 10_000;
    private static final int FAILURE_ALERT_THRESHOLD = 3;

    private final NotifyDueGroupDosesUseCase notifyDueGroupDoses;
    private final Counter successCounter;
    private final Counter failureCounter;
    private final SlackNotifier slackNotifier;
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);

    public GroupDoseNotificationPoller(NotifyDueGroupDosesUseCase notifyDueGroupDoses,
                                       MeterRegistry registry,
                                       SlackNotifier slackNotifier) {
        this.notifyDueGroupDoses = notifyDueGroupDoses;
        this.slackNotifier = slackNotifier;
        this.successCounter = Counter.builder("pillmate.notification.poller.runs")
                .tag("result", "success")
                .description("Group dose notification poller successful runs")
                .register(registry);
        this.failureCounter = Counter.builder("pillmate.notification.poller.runs")
                .tag("result", "failure")
                .description("Group dose notification poller failed runs")
                .register(registry);
    }

    @Scheduled(fixedDelay = POLL_INTERVAL_MS)
    public void poll() {
        try {
            int sent = notifyDueGroupDoses.notifyDue();
            successCounter.increment();
            consecutiveFailures.set(0);
            if (sent > 0) {
                log.info("GroupDoseNotificationPoller sent={}", sent);
            }
        } catch (RuntimeException ex) {
            failureCounter.increment();
            log.error("GroupDoseNotificationPoller failed reason={}", ex.getMessage(), ex);
            int failures = consecutiveFailures.incrementAndGet();
            if (failures >= FAILURE_ALERT_THRESHOLD) {
                slackNotifier.send(String.format(
                        "⚠️ 그룹 알림 폴러 %d회 연속 실패 reason=%s", failures, ex.getClass().getSimpleName()));
                consecutiveFailures.set(0);
            }
        }
    }
}
