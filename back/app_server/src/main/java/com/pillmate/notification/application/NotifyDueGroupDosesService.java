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
    // V27 이전 기존 TAKEN 행(group_notified_at=NULL) 폭주 방지 — 윈도우 밖 과거 행은 영구 미발송
    private static final Duration RECENCY_WINDOW = Duration.ofMinutes(10);

    private final DoseLogRepository doseLogRepository;
    private final SendGroupDoseNotificationService sendGroupDoseNotificationService;
    private final Clock clock;

    @Override
    public int notifyDue() {
        Instant now = Instant.now(clock);
        Instant windowStart = now.minus(RECENCY_WINDOW);
        Instant cutoff = now.minus(GROUP_NOTIFY_DELAY);
        List<DoseLog> due = doseLogRepository.findTakenNotGroupNotifiedBetween(windowStart, cutoff);
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
