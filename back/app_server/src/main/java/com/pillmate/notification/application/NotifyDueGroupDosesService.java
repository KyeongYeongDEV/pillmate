package com.pillmate.notification.application;

import com.pillmate.doselog.domain.model.DoseLog;
import com.pillmate.doselog.domain.repository.DoseLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotifyDueGroupDosesService implements NotifyDueGroupDosesUseCase {

    private static final Duration GROUP_NOTIFY_DELAY = Duration.ofSeconds(60);

    private final DoseLogRepository doseLogRepository;
    private final SendGroupDoseNotificationService sendGroupDoseNotificationService;
    private final Clock clock;

    @Override
    public int notifyDue() {
        Instant cutoff = Instant.now(clock).minus(GROUP_NOTIFY_DELAY);
        List<DoseLog> due = doseLogRepository.findTakenNotGroupNotifiedBefore(cutoff);
        due.forEach(this::notifySafely);
        return due.size();
    }

    private void notifySafely(DoseLog doseLog) {
        try {
            sendGroupDoseNotificationService.send(doseLog.getId(), doseLog.getCheckedBy());
        } catch (RuntimeException ex) {
            log.warn("그룹 복약 알림 발송 실패 doseLogId={} reason={}", doseLog.getId(), ex.getMessage());
        }
    }
}
