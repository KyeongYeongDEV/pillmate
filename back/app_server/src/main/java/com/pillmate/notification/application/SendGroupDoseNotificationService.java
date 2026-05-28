package com.pillmate.notification.application;

import com.pillmate.caregroup.domain.repository.MembershipRepository;
import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.doselog.domain.model.DoseLog;
import com.pillmate.doselog.domain.model.DoseStatus;
import com.pillmate.doselog.domain.repository.DoseLogRepository;
import com.pillmate.notification.application.port.NotificationSenderPort;
import com.pillmate.notification.domain.model.Notification;
import com.pillmate.notification.domain.repository.NotificationRepository;
import com.pillmate.schedule.domain.model.Schedule;
import com.pillmate.schedule.domain.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SendGroupDoseNotificationService {

    private final DoseLogRepository doseLogRepository;
    private final ScheduleRepository scheduleRepository;
    private final MembershipRepository membershipRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationSenderPort notificationSenderPort;

    @Transactional
    public void send(Long doseLogId, Long actorUserId) {
        DoseLog doseLog = findDoseLog(doseLogId);
        Schedule schedule = findSchedule(doseLog.getScheduleId());
        List<Long> recipientIds = findGroupMembers(actorUserId);

        if (recipientIds.isEmpty()) {
            return;
        }

        List<Notification> notifications = buildNotifications(
                doseLog, actorUserId, schedule.getCareGroupId(), recipientIds);

        List<Notification> saved = notificationRepository.saveAll(notifications);
        saved.forEach(notificationSenderPort::send);
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
