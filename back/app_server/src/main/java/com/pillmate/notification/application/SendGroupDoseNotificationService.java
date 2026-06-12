package com.pillmate.notification.application;

import com.pillmate.caregroup.domain.repository.MembershipRepository;
import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.doselog.domain.model.DoseLog;
import com.pillmate.doselog.domain.model.DoseStatus;
import com.pillmate.doselog.domain.repository.DoseLogRepository;
import com.pillmate.notification.application.port.NotificationSenderPort;
import com.pillmate.notification.application.port.NotificationSenderPort.NotificationCommand;
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
    private final Clock clock;

    public void send(Long doseLogId, Long actorUserId) {
        DoseLog doseLog = findDoseLog(doseLogId);
        if (doseLog.isGroupNotified()) {
            return;
        }
        Schedule schedule = findSchedule(doseLog.getScheduleId());
        markGroupNotified(doseLog);

        List<Long> recipientIds = findGroupMembers(actorUserId);
        if (recipientIds.isEmpty()) {
            return;
        }

        List<Notification> notifications = buildNotifications(
                doseLog, actorUserId, schedule.getCareGroupId(), recipientIds);
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
                Map.of("route", "/group/" + n.getCareGroupId())
        );
    }

    private DoseLog findDoseLog(Long doseLogId) {
        return doseLogRepository.findById(doseLogId)
                .orElseThrow(() -> new PillmateException(ErrorCode.INVALID_NOTIFICATION_DOSE_LOG));
    }

    private Schedule findSchedule(Long scheduleId) {
        return scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new PillmateException(ErrorCode.SCHEDULE_NOT_FOUND));
    }

    private List<Long> findGroupMembers(Long actorUserId) {
        return membershipRepository.findGroupMemberUserIds(actorUserId);
    }

    private List<Notification> buildNotifications(DoseLog doseLog, Long actorUserId,
                                                   Long careGroupId, List<Long> recipientIds) {
        boolean isMissed = doseLog.getStatus() == DoseStatus.SKIPPED
                || doseLog.getStatus() == DoseStatus.MISSED;
        return recipientIds.stream()
                .filter(id -> !id.equals(actorUserId))
                .map(recipientId -> isMissed
                        ? Notification.doseMissed(recipientId, actorUserId, careGroupId, doseLog.getId())
                        : Notification.doseTaken(recipientId, actorUserId, careGroupId, doseLog.getId()))
                .toList();
    }
}
