package com.pillmate.notification.application;

import com.pillmate.notification.domain.model.Notification;
import com.pillmate.notification.domain.model.NotificationStatus;
import com.pillmate.notification.domain.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationPersistenceService {

    private final NotificationRepository notificationRepository;

    // AFTER_COMMIT 리스너에서 호출됨 — REQUIRED 는 이미 커밋된 트랜잭션에 참여해 flush 유실 (P1-B)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<Notification> saveAll(List<Notification> notifications) {
        return notificationRepository.saveAll(notifications);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSent(Long notificationId, Instant when) {
        notificationRepository.findById(notificationId)
                .ifPresent(n -> n.markSent(when));
    }

    // 건당 REQUIRES_NEW N번(N 트랜잭션·N 왕복) 대신 배치 UPDATE 1번 — 2026-09-27 FCM 성능개선.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSentAll(List<Long> notificationIds, Instant when) {
        if (notificationIds.isEmpty()) {
            return;
        }
        notificationRepository.markSentByIdIn(notificationIds, when, NotificationStatus.SENT);
    }
}
