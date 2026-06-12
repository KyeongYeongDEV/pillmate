package com.pillmate.notification.infrastructure.scheduler;

import com.pillmate.notification.application.NotifyDueGroupDosesUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GroupDoseNotificationPoller {

    private static final long POLL_INTERVAL_MS = 10_000;

    private final NotifyDueGroupDosesUseCase notifyDueGroupDoses;

    @Scheduled(fixedDelay = POLL_INTERVAL_MS)
    public void poll() {
        try {
            int sent = notifyDueGroupDoses.notifyDue();
            if (sent > 0) {
                log.info("GroupDoseNotificationPoller sent={}", sent);
            }
        } catch (RuntimeException ex) {
            log.error("GroupDoseNotificationPoller failed reason={}", ex.getMessage(), ex);
        }
    }
}
