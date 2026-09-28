package com.pillmate.notification.domain.repository;

import com.pillmate.notification.domain.model.Notification;
import com.pillmate.notification.domain.model.NotificationStatus;
import com.pillmate.notification.domain.model.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipientUserIdOrderByCreatedAtDesc(Long recipientUserId);

    @Modifying
    @Query("""
            UPDATE Notification n
            SET n.status = :readStatus, n.readAt = :now
            WHERE n.recipientUserId = :userId AND n.status = :sentStatus
            """)
    int markAllReadByUser(
            @Param("userId") Long userId,
            @Param("now") Instant now,
            @Param("readStatus") NotificationStatus readStatus,
            @Param("sentStatus") NotificationStatus sentStatus
    );

    // FCM 발송 성공 확인 후 상태 반영 — id 목록을 배치 UPDATE 1회로 (건당 REQUIRES_NEW N번 대신, 2026-09-27 성능개선)
    @Modifying
    @Query("""
            UPDATE Notification n
            SET n.status = :sentStatus, n.sentAt = :now
            WHERE n.id IN :ids
            """)
    int markSentByIdIn(
            @Param("ids") List<Long> ids,
            @Param("now") Instant now,
            @Param("sentStatus") NotificationStatus sentStatus
    );

    // Polling Outbox 재시도 스위퍼 — PENDING 에 방치된(발송 시도했으나 확인 안 된) 안전관련 알림만 재조회.
    // (2026-09-27) DOSE_REMINDER/DOSE_OVERDUE 처럼 "최소 1번은 가야 하는" 알림에 한정 적용.
    // 최초 stuck(nextRetryAt IS NULL)은 createdAt 기준, 이미 재시도해 backoff 잡힌 건은 nextRetryAt 기준으로 게이트.
    @Query("""
            SELECT n FROM Notification n
            WHERE n.status = :pendingStatus
              AND n.type IN :types
              AND n.retryCount < :maxRetries
              AND (
                    (n.nextRetryAt IS NULL AND n.createdAt < :before)
                 OR (n.nextRetryAt IS NOT NULL AND n.nextRetryAt <= :now)
              )
            """)
    List<Notification> findStuckPendingByTypesBefore(
            @Param("pendingStatus") NotificationStatus pendingStatus,
            @Param("types") List<NotificationType> types,
            @Param("before") Instant before,
            @Param("now") Instant now,
            @Param("maxRetries") int maxRetries
    );

    // 재시도 실패(한도 미달) — retryCount 증가 + backoff+jitter 로 계산한 다음 시도 시각 기록.
    // jitter 가 행마다 달라 배치 UPDATE 불가하나, 재시도-실패 행은 드문 예외경로라 건별 처리 허용.
    @Modifying
    @Query("UPDATE Notification n SET n.retryCount = n.retryCount + 1, n.nextRetryAt = :nextRetryAt WHERE n.id = :id")
    int scheduleNextRetry(@Param("id") Long id, @Param("nextRetryAt") Instant nextRetryAt);

    @Modifying
    @Query("UPDATE Notification n SET n.status = :failedStatus WHERE n.id IN :ids")
    int markFailedByIdIn(@Param("ids") List<Long> ids, @Param("failedStatus") NotificationStatus failedStatus);
}
