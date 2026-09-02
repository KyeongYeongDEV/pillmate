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

// 예정시각+30분 경과 PENDING dose → 케어그룹 전원(당사자 본인 + 다른 ACTIVE 그룹원) 지연 알림.
// 원자 클레임(markOverdueNotifiedIfPending) 선행 — DoseReminderService 패턴과 동일 (동시성/중복발송 차단).
@Slf4j
@Service
@RequiredArgsConstructor
public class NotifyOverdueDosesService implements NotifyOverdueDosesUseCase {

    private static final Duration OVERDUE_THRESHOLD = Duration.ofMinutes(30);
    // 방금 임계값을 넘긴 행만 대상 — 과거 PENDING 행 폭주 방지 (그룹 알림/리마인더 폴러 RECENCY_WINDOW 선례)
    private static final Duration RECENCY_WINDOW = Duration.ofMinutes(10);

    private final DoseLogRepository doseLogRepository;
    private final SendOverdueDoseNotificationService sendOverdueDoseNotificationService;
    private final Clock clock;

    @Override
    public int notifyDue() {
        Instant now = Instant.now(clock);
        Instant cutoff = now.minus(OVERDUE_THRESHOLD);
        Instant windowStart = cutoff.minus(RECENCY_WINDOW);
        List<DoseLog> due = doseLogRepository.findPendingOverdueNotNotifiedBetween(windowStart, cutoff);
        due.forEach(this::notifySafely);
        return due.size();
    }

    private void notifySafely(DoseLog doseLog) {
        try {
            notify(doseLog);
        } catch (RuntimeException ex) {
            log.warn("복약 지연 알림 처리 실패 doseLogId={} reason={}", doseLog.getId(), ex.getMessage());
        }
    }

    private void notify(DoseLog doseLog) {
        if (!claimOverdue(doseLog)) {
            return;
        }
        sendOverdueDoseNotificationService.send(doseLog.getId());
    }

    private boolean claimOverdue(DoseLog doseLog) {
        return doseLogRepository.markOverdueNotifiedIfPending(doseLog.getId(), Instant.now(clock)) == 1;
    }
}
