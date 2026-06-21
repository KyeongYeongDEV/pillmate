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

    @Column
    private Long referenceId;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private NotificationReferenceType referenceType;

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

    public static Notification doseTaken(Long recipientUserId, Long actorUserId,
                                         Long careGroupId, Long doseLogId,
                                         Long prescriptionId, String prescriptionName) {
        return doseForPrescription(recipientUserId, actorUserId, careGroupId, doseLogId,
                prescriptionId, NotificationType.DOSE_TAKEN,
                "'" + prescriptionName + "' 복약을 완료했어요");
    }

    public static Notification doseMissed(Long recipientUserId, Long actorUserId,
                                          Long careGroupId, Long doseLogId,
                                          Long prescriptionId, String prescriptionName) {
        return doseForPrescription(recipientUserId, actorUserId, careGroupId, doseLogId,
                prescriptionId, NotificationType.DOSE_MISSED,
                "'" + prescriptionName + "' 복약을 건너뛰었어요");
    }

    private static Notification doseForPrescription(Long recipientUserId, Long actorUserId,
                                                    Long careGroupId, Long doseLogId, Long prescriptionId,
                                                    NotificationType type, String body) {
        Notification n = create(recipientUserId, actorUserId, careGroupId, doseLogId, type, "복약 알림", body);
        n.referenceId = prescriptionId;
        n.referenceType = NotificationReferenceType.PRESCRIPTION;
        return n;
    }

    public static Notification doseCanceled(Long recipientUserId, Long actorUserId,
                                            Long careGroupId, Long doseLogId,
                                            String actorName, String timeOfDayLabel) {
        String body = actorName + "님이 " + timeOfDayLabel + " 약 복용을 취소했습니다";
        return create(recipientUserId, actorUserId, careGroupId, doseLogId,
                NotificationType.DOSE_CANCELED, "복약 알림", body);
    }

    public static Notification ddiCritical(Long recipientUserId, Long actorUserId,
                                            Long careGroupId, Long prescriptionId,
                                            String warningDetail) {
        String body = "병용 주의 약물이 발견되었습니다. " + warningDetail
                + " 반드시 약사 또는 의사와 상담하세요.";
        return createWithReference(recipientUserId, actorUserId, careGroupId,
                prescriptionId, NotificationReferenceType.PRESCRIPTION,
                NotificationType.DDI_CRITICAL, "⚠️ 약물 상호작용 주의", body);
    }

    public static Notification prescriptionNew(Long recipientUserId, Long actorUserId,
                                                Long careGroupId, Long prescriptionId) {
        return createWithReference(recipientUserId, actorUserId, careGroupId,
                prescriptionId, NotificationReferenceType.PRESCRIPTION,
                NotificationType.PRESCRIPTION_NEW, "새 처방전 등록", "그룹 멤버의 새 처방전이 등록되었어요");
    }

    public static Notification weeklyReport(Long recipientUserId, Long actorUserId,
                                             Long careGroupId, Long reportId) {
        return createWithReference(recipientUserId, actorUserId, careGroupId,
                reportId, NotificationReferenceType.REPORT,
                NotificationType.WEEKLY_REPORT, "주간 리포트 도착", "이번 주 복약 리포트를 확인해 보세요");
    }

    private static Notification createWithReference(Long recipientUserId, Long actorUserId,
                                                     Long careGroupId,
                                                     Long refId, NotificationReferenceType refType,
                                                     NotificationType type, String title, String body) {
        Notification n = create(recipientUserId, actorUserId, careGroupId, null, type, title, body);
        n.referenceId = refId;
        n.referenceType = refType;
        return n;
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
