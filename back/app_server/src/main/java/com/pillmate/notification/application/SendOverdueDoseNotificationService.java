package com.pillmate.notification.application;

import com.pillmate.caregroup.domain.model.Membership;
import com.pillmate.caregroup.domain.repository.MembershipRepository;
import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.doselog.domain.model.DoseLog;
import com.pillmate.doselog.domain.repository.DoseLogRepository;
import com.pillmate.notification.application.port.NotificationSenderPort;
import com.pillmate.notification.application.port.NotificationSenderPort.NotificationCommand;
import com.pillmate.notification.application.port.RecipientCachePort;
import com.pillmate.notification.application.port.RecipientCachePort.CachedRecipient;
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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// 지연(예정시각+30분) 복약 알림 발송 — 당사자 본인(2인칭, dose-reminder 채널) + 다른 ACTIVE 그룹원(3인칭, group-activity 채널).
@Slf4j
@Service
@RequiredArgsConstructor
public class SendOverdueDoseNotificationService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter TIME_LABEL = DateTimeFormatter.ofPattern("a h시", Locale.KOREAN);
    private static final String ROUTE_HOME = "/home";
    private static final String DATA_KEY_CHANNEL = "channel";
    private static final String CHANNEL_DOSE_REMINDER = "dose-reminder";

    private final DoseLogRepository doseLogRepository;
    private final ScheduleRepository scheduleRepository;
    private final MembershipRepository membershipRepository;
    private final NotificationPersistenceService notificationPersistenceService;
    private final UserRepository userRepository;
    private final NotificationSenderPort notificationSenderPort;
    private final RecipientCachePort recipientCachePort;
    private final Clock clock;

    public void send(Long doseLogId) {
        DoseLog doseLog = findDoseLog(doseLogId);
        Schedule schedule = findSchedule(doseLog.getScheduleId());
        if (!schedule.isActive()) {
            log.warn("복약 지연 알림 스케줄 비활성 doseLogId={} scheduleId={}", doseLogId, schedule.getId());
            return;
        }

        String timeLabel = formatTimeLabel(doseLog.getScheduledAt());
        User patient = findPatient(doseLog.getPatientId());
        List<CachedRecipient> groupRecipients = loadGroupRecipients(schedule.getCareGroupId());

        List<Notification> notifications =
                buildNotifications(doseLog, schedule.getCareGroupId(), timeLabel, patient, groupRecipients);
        Map<Long, String> tokensByUserId = buildTokenMap(doseLog.getPatientId(), patient, groupRecipients);

        List<Notification> saved = notificationPersistenceService.saveAll(notifications);
        dispatchAll(saved, tokensByUserId, doseLog.getPatientId());
    }

    private List<Notification> buildNotifications(DoseLog doseLog, Long careGroupId, String timeLabel,
                                                    User patient, List<CachedRecipient> groupRecipients) {
        List<Notification> notifications = new ArrayList<>();
        notifications.add(Notification.doseOverdueSelf(doseLog.getPatientId(), careGroupId, doseLog.getId(), timeLabel));
        if (careGroupId != null) {
            String patientName = patient == null ? null : patient.getName();
            notifications.addAll(buildGroupNotifications(doseLog, careGroupId, timeLabel, patientName, groupRecipients));
        }
        return notifications;
    }

    private List<Notification> buildGroupNotifications(DoseLog doseLog, Long careGroupId, String timeLabel,
                                                         String patientName, List<CachedRecipient> groupRecipients) {
        return groupRecipients.stream()
                .map(CachedRecipient::userId)
                .filter(id -> !id.equals(doseLog.getPatientId()))
                .map(id -> Notification.doseOverdueGroup(
                        id, doseLog.getPatientId(), careGroupId, doseLog.getId(), patientName, timeLabel))
                .toList();
    }

    private Map<Long, String> buildTokenMap(Long patientId, User patient, List<CachedRecipient> groupRecipients) {
        Map<Long, String> tokens = new HashMap<>(tokensByUserId(groupRecipients));
        tokens.put(patientId, patient == null ? null : patient.getExpoPushToken());
        return tokens;
    }

    // 그룹 수신자+토큰 — 캐시 우선(TTL 5m), miss 시 DB 조회 후 적재 (SendGroupDoseNotificationService 선례)
    private List<CachedRecipient> loadGroupRecipients(Long careGroupId) {
        if (careGroupId == null) {
            return List.of();
        }
        return recipientCachePort.get(careGroupId)
                .orElseGet(() -> loadAndCacheRecipients(careGroupId));
    }

    private List<CachedRecipient> loadAndCacheRecipients(Long careGroupId) {
        List<Long> memberIds = membershipRepository.findByCareGroupId(careGroupId).stream()
                .map(Membership::getUserId)
                .toList();
        if (memberIds.isEmpty()) {
            return List.of();
        }
        Map<Long, String> tokens = new HashMap<>();
        userRepository.findAllByIdIn(memberIds).forEach(u -> tokens.put(u.getId(), u.getExpoPushToken()));
        List<CachedRecipient> recipients = memberIds.stream()
                .map(id -> new CachedRecipient(id, tokens.get(id)))
                .toList();
        recipientCachePort.put(careGroupId, recipients);
        return recipients;
    }

    private Map<Long, String> tokensByUserId(List<CachedRecipient> recipients) {
        Map<Long, String> tokens = new HashMap<>();
        recipients.stream()
                .filter(r -> r.token() != null)
                .forEach(r -> tokens.put(r.userId(), r.token()));
        return tokens;
    }

    private void dispatchAll(List<Notification> saved, Map<Long, String> tokensByUserId, Long patientId) {
        List<NotificationCommand> commands = saved.stream()
                .map(n -> toCommand(n, tokensByUserId.get(n.getRecipientUserId()), patientId))
                .toList();
        List<Long> sentIds = notificationSenderPort.sendAll(commands);
        markSentAll(sentIds);
    }

    private NotificationCommand toCommand(Notification n, String token, Long patientId) {
        Map<String, String> data = new HashMap<>();
        data.put("route", ROUTE_HOME);
        data.put("type", n.getType().name());
        if (n.getRecipientUserId().equals(patientId)) {
            data.put(DATA_KEY_CHANNEL, CHANNEL_DOSE_REMINDER);
        }
        return new NotificationCommand(n.getId(), n.getRecipientUserId(), token, n.getTitle(), n.getBody(), data);
    }

    private void markSentAll(List<Long> sentNotificationIds) {
        Instant now = Instant.now(clock);
        sentNotificationIds.forEach(id -> notificationPersistenceService.markSent(id, now));
    }

    private String formatTimeLabel(Instant scheduledAt) {
        return scheduledAt.atZone(KST).format(TIME_LABEL);
    }

    private User findPatient(Long patientId) {
        return userRepository.findById(patientId).orElse(null);
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
