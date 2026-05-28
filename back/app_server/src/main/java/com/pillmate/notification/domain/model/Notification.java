package com.pillmate.notification.domain.model;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long recipientUserId;

    @Column(nullable = false)
    private Long actorUserId;

    @Column(nullable = false)
    private Long careGroupId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationType type;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 280)
    private String body;

    @Column
    private Long doseLogId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column
    private Instant sentAt;

    @Column
    private Instant readAt;

    public static Notification doseTaken(Long recipientUserId, Long actorUserId,
                                         Long careGroupId, Long doseLogId) {
        return create(recipientUserId, actorUserId, careGroupId, doseLogId,
                NotificationType.DOSE_TAKEN, "복약 알림", "그룹 멤버가 복약을 완료했어요");
    }

    public static Notification doseMissed(Long recipientUserId, Long actorUserId,
                                          Long careGroupId, Long doseLogId) {
        return create(recipientUserId, actorUserId, careGroupId, doseLogId,
                NotificationType.DOSE_MISSED, "복약 알림", "그룹 멤버가 복약을 건너뛰었어요");
    }

    private static Notification create(Long recipientUserId, Long actorUserId,
                                        Long careGroupId, Long doseLogId,
                                        NotificationType type, String title, String body) {
        Notification n = new Notification();
        n.recipientUserId = recipientUserId;
        n.actorUserId = actorUserId;
        n.careGroupId = careGroupId;
        n.doseLogId = doseLogId;
        n.type = type;
        n.title = title;
        n.body = body;
        n.status = NotificationStatus.PENDING;
        n.createdAt = Instant.now();
        return n;
    }

    public void markSent(Instant now) {
        this.status = NotificationStatus.SENT;
        this.sentAt = now;
    }

    public void markRead(Instant now) {
        if (this.status == NotificationStatus.PENDING) {
            throw new PillmateException(ErrorCode.NOT_NOTIFICATION_OWNER);
        }
        this.status = NotificationStatus.READ;
        this.readAt = now;
    }
}
