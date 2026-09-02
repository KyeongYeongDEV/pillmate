package com.pillmate.notification.application;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.common.security.CareGroupGuard;
import com.pillmate.doselog.domain.model.DoseLog;
import com.pillmate.doselog.domain.model.DoseStatus;
import com.pillmate.doselog.domain.repository.DoseLogRepository;
import com.pillmate.notification.application.port.NotificationSenderPort;
import com.pillmate.notification.application.port.NotificationSenderPort.NotificationCommand;
import com.pillmate.notification.application.dto.NudgeResponse;
import com.pillmate.notification.application.port.NudgeCooldownPort;
import com.pillmate.notification.domain.model.Notification;
import com.pillmate.schedule.domain.model.Schedule;
import com.pillmate.schedule.domain.repository.ScheduleRepository;
import com.pillmate.user.domain.model.User;
import com.pillmate.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

// 그룹원 → 당사자 수동 넛지 ("약 챙기라고 알려드려요"). ACTIVE 그룹원만, PENDING dose 만,
// (dose,발신자) 쌍 10분 쿨다운 + 당사자(수신자) 단위 10분 총량 캡 1건 (스팸 방지).
@Service
@RequiredArgsConstructor
public class SendDoseNudgeService {

    private static final Duration COOLDOWN = Duration.ofMinutes(10);
    private static final String ROUTE_HOME = "/home";
    private static final String CHANNEL_DOSE_REMINDER = "dose-reminder";

    private final DoseLogRepository doseLogRepository;
    private final ScheduleRepository scheduleRepository;
    private final CareGroupGuard careGroupGuard;
    private final NudgeCooldownPort nudgeCooldownPort;
    private final UserRepository userRepository;
    private final NotificationPersistenceService notificationPersistenceService;
    private final NotificationSenderPort notificationSenderPort;
    private final Clock clock;

    public NudgeResponse nudge(Long doseLogId, Long fromUserId) {
        DoseLog doseLog = findDoseLog(doseLogId);
        Schedule schedule = findSchedule(doseLog.getScheduleId());
        careGroupGuard.requireAccessible(schedule.getCareGroupId());
        requirePending(doseLog);
        requireCooldownAvailable(doseLogId, fromUserId);

        if (!nudgeCooldownPort.acquireRecipientCap(doseLog.getPatientId(), COOLDOWN)) {
            return new NudgeResponse(true);
        }

        Notification notification = Notification.doseNudge(
                doseLog.getPatientId(), fromUserId, schedule.getCareGroupId(), doseLogId, resolveActorName(fromUserId));
        Notification saved = notificationPersistenceService.saveAll(List.of(notification)).get(0);
        List<Long> sentIds = notificationSenderPort.sendAll(List.of(toCommand(saved)));
        markSentAll(sentIds);
        return new NudgeResponse(false);
    }

    private void requirePending(DoseLog doseLog) {
        if (doseLog.getStatus() != DoseStatus.PENDING) {
            throw new PillmateException(ErrorCode.DOSE_LOG_ALREADY_CHECKED);
        }
    }

    private void requireCooldownAvailable(Long doseLogId, Long fromUserId) {
        if (!nudgeCooldownPort.tryAcquire(doseLogId, fromUserId, COOLDOWN)) {
            throw new PillmateException(ErrorCode.NUDGE_COOLDOWN_ACTIVE);
        }
    }

    private NotificationCommand toCommand(Notification n) {
        String token = userRepository.findById(n.getRecipientUserId())
                .map(User::getExpoPushToken)
                .orElse(null);
        Map<String, String> data = Map.of(
                "route", ROUTE_HOME,
                "type", n.getType().name(),
                "channel", CHANNEL_DOSE_REMINDER);
        return new NotificationCommand(n.getId(), n.getRecipientUserId(), token, n.getTitle(), n.getBody(), data);
    }

    private void markSentAll(List<Long> sentNotificationIds) {
        Instant now = Instant.now(clock);
        sentNotificationIds.forEach(id -> notificationPersistenceService.markSent(id, now));
    }

    private String resolveActorName(Long userId) {
        return userRepository.findById(userId).map(User::getName).orElse(null);
    }

    private DoseLog findDoseLog(Long doseLogId) {
        return doseLogRepository.findById(doseLogId)
                .orElseThrow(() -> new PillmateException(ErrorCode.INVALID_NOTIFICATION_DOSE_LOG));
    }

    private Schedule findSchedule(Long scheduleId) {
        return scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new PillmateException(ErrorCode.SCHEDULE_NOT_FOUND));
    }
}
