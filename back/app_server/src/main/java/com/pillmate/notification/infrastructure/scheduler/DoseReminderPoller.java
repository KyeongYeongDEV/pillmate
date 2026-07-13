package com.pillmate.notification.infrastructure.scheduler;

import com.pillmate.common.monitoring.SlackNotifier;
import com.pillmate.notification.application.NotifyDueDoseRemindersUseCase;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class DoseReminderPoller {

    // 리마인더는 분 단위 정확도면 충분 — 그룹 폴러(10s)와 부하 분리
    private static final long POLL_INTERVAL_MS = 30_000;
    private static final int FAILURE_ALERT_THRESHOLD = 3;

    private final NotifyDueDoseRemindersUseCase notifyDueDoseReminders;
    private final Counter successCounter;
    private final Counter failureCounter;
    private final SlackNotifier slackNotifier;
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);

    public DoseReminderPoller(NotifyDueDoseRemindersUseCase notifyDueDoseReminders,
                              MeterRegistry registry,
                              SlackNotifier slackNotifier) {
        this.notifyDueDoseReminders = notifyDueDoseReminders;
        this.slackNotifier = slackNotifier;
        this.successCounter = Counter.builder("pillmate.notification.reminder.poller.runs")
                .tag("result", "success")
                .description("Dose reminder poller successful runs")
                .register(registry);
        this.failureCounter = Counter.builder("pillmate.notification.reminder.poller.runs")
                .tag("result", "failure")
                .description("Dose reminder poller failed runs")
                .register(registry);
    }

    @Scheduled(fixedDelay = POLL_INTERVAL_MS)
    public void poll() {
        try {
            int sent = notifyDueDoseReminders.notifyDue();
            successCounter.increment();
            consecutiveFailures.set(0);
            if (sent > 0) {
                log.info("DoseReminderPoller sent={}", sent);
            }
        } catch (RuntimeException ex) {
            failureCounter.increment();
            log.error("DoseReminderPoller failed reason={}", ex.getMessage(), ex);
            int failures = consecutiveFailures.incrementAndGet();
            if (failures >= FAILURE_ALERT_THRESHOLD) {
                slackNotifier.send(String.format(
                        "⚠️ 복약 리마인더 폴러 %d회 연속 실패 reason=%s", failures, ex.getClass().getSimpleName()));
                consecutiveFailures.set(0);
            }
        }
    }
}
