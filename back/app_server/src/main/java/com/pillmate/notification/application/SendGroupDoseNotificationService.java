package com.pillmate.notification.application;

import com.pillmate.caregroup.domain.model.Membership;
import com.pillmate.caregroup.domain.repository.MembershipRepository;
import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.common.prescription.PrescriptionLabel;
import com.pillmate.doselog.domain.model.DoseLog;
import com.pillmate.doselog.domain.model.DoseStatus;
import com.pillmate.doselog.domain.repository.DoseLogRepository;
import com.pillmate.notification.application.port.CareGroupLookupPort;
import com.pillmate.notification.application.port.NotificationSenderPort;
import com.pillmate.notification.application.port.NotificationSenderPort.NotificationCommand;
import com.pillmate.notification.application.port.PrescriptionSummaryPort;
import com.pillmate.notification.domain.model.Notification;
import com.pillmate.schedule.domain.model.Schedule;
import com.pillmate.schedule.domain.repository.ScheduleRepository;
import com.pillmate.user.domain.model.User;
import com.pillmate.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SendGroupDoseNotificationService {

    private final DoseLogRepository doseLogRepository;
    private final ScheduleRepository scheduleRepository;
    private final MembershipRepository membershipRepository;
    private final NotificationPersistenceService notificationPersistenceService;
    private final UserRepository userRepository;
    private final NotificationSenderPort notificationSenderPort;
    private final PrescriptionSummaryPort prescriptionSummaryPort;
    private final CareGroupLookupPort careGroupLookupPort;
    private final Clock clock;

    public void send(Long doseLogId, Long actorUserId) {
        DoseLog doseLog = findDoseLog(doseLogId);
        if (doseLog.isGroupNotified()) {
            return;
        }
        Schedule schedule = findSchedule(doseLog.getScheduleId());
        markGroupNotified(doseLog);

        List<Long> recipientIds = findGroupMembersByGroup(schedule.getCareGroupId());
        if (recipientIds.isEmpty()) {
            return;
        }

        List<Notification> notifications = buildNotifications(
                doseLog, actorUserId, schedule, recipientIds);
        if (notifications.isEmpty()) {
            return;
        }

        List<Notification> saved = notificationPersistenceService.saveAll(notifications);
        saved.forEach(this::dispatchOne);
    }

    private void markGroupNotified(DoseLog doseLog) {
        doseLog.markGroupNotified(Instant.now(clock));
        doseLogRepository.save(doseLog);
    }

    private void dispatchOne(Notification notification) {
        String token = lookupToken(notification.getRecipientUserId());
        try {
            notificationSenderPort.send(toCommand(notification, token));
            notificationPersistenceService.markSent(notification.getId(), Instant.now());
        } catch (Exception e) {
            log.warn("푸시 발송 실패 notificationId={} reason={}", notification.getId(), e.getMessage());
        }
    }

    private String lookupToken(Long recipientUserId) {
        return userRepository.findById(recipientUserId)
                .map(User::getExpoPushToken)
                .orElse(null);
    }

    private NotificationCommand toCommand(Notification n, String token) {
        return new NotificationCommand(
                n.getId(),
                n.getRecipientUserId(),
                token,
                n.getTitle(),
                n.getBody(),
                Map.of("route", resolveRoute(n))
        );
    }

    private String resolveRoute(Notification n) {
        Long careGroupId = n.getCareGroupId();
        return careGroupId != null ? "/group/" + careGroupId : "/home";
    }

    private DoseLog findDoseLog(Long doseLogId) {
        return doseLogRepository.findById(doseLogId)
                .orElseThrow(() -> new PillmateException(ErrorCode.INVALID_NOTIFICATION_DOSE_LOG));
    }

    private Schedule findSchedule(Long scheduleId) {
        return scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new PillmateException(ErrorCode.SCHEDULE_NOT_FOUND));
    }

    private List<Long> findGroupMembersByGroup(Long careGroupId) {
        if (careGroupId == null) return List.of();
        return membershipRepository.findByCareGroupId(careGroupId).stream()
                .map(Membership::getUserId)
                .toList();
    }

    private List<Notification> buildNotifications(DoseLog doseLog, Long actorUserId,
                                                   Schedule schedule, List<Long> recipientIds) {
        boolean isMissed = doseLog.getStatus() == DoseStatus.SKIPPED
                || doseLog.getStatus() == DoseStatus.MISSED;
        Long prescriptionId = schedule.getPrescriptionId();
        String prescriptionName = resolvePrescriptionName(prescriptionId);
        Long careGroupId = schedule.getCareGroupId();
        String actorName = resolveActorName(actorUserId);
        String groupName = resolveGroupName(careGroupId);
        return recipientIds.stream()
                .filter(id -> !id.equals(actorUserId))
                .filter(id -> !id.equals(doseLog.getPatientId()))
                .map(recipientId -> buildOne(
                        isMissed, recipientId, actorUserId, careGroupId, doseLog.getId(),
                        prescriptionId, prescriptionName, actorName, groupName))
                .toList();
    }

    private Notification buildOne(boolean isMissed, Long recipientId, Long actorUserId, Long careGroupId,
                                   Long doseLogId, Long prescriptionId, String prescriptionName,
                                   String actorName, String groupName) {
        if (prescriptionName == null) {
            return isMissed
                    ? Notification.doseMissed(recipientId, actorUserId, careGroupId, doseLogId, actorName, groupName)
                    : Notification.doseTaken(recipientId, actorUserId, careGroupId, doseLogId, actorName, groupName);
        }
        return isMissed
                ? Notification.doseMissed(recipientId, actorUserId, careGroupId, doseLogId, prescriptionId, prescriptionName, actorName, groupName)
                : Notification.doseTaken(recipientId, actorUserId, careGroupId, doseLogId, prescriptionId, prescriptionName, actorName, groupName);
    }

    private String resolveActorName(Long userId) {
        if (userId == null) return null;
        return userRepository.findById(userId).map(User::getName).orElse(null);
    }

    private String resolveGroupName(Long careGroupId) {
        if (careGroupId == null) return null;
        return careGroupLookupPort.findNameById(careGroupId).orElse(null);
    }

    private String resolvePrescriptionName(Long prescriptionId) {
        if (prescriptionId == null) {
            return null;
        }
        return prescriptionSummaryPort.findById(prescriptionId)
                .map(summary -> PrescriptionLabel.of(
                        summary.prescribedAt(), summary.leadDrugName(), summary.drugCount()))
                .orElse(null);
    }
}
