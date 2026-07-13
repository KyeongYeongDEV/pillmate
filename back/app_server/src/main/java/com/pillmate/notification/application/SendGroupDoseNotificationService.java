package com.pillmate.notification.application;

import com.pillmate.caregroup.domain.model.Membership;
import com.pillmate.caregroup.domain.repository.MembershipRepository;
import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.doselog.domain.model.DoseLog;
import com.pillmate.doselog.domain.model.DoseStatus;
import com.pillmate.doselog.domain.repository.DoseLogRepository;
import com.pillmate.notification.application.port.CareGroupLookupPort;
import com.pillmate.notification.application.port.NotificationSenderPort;
import com.pillmate.notification.application.port.NotificationSenderPort.NotificationCommand;
import com.pillmate.notification.application.port.PrescriptionSummaryPort;
import com.pillmate.notification.application.port.PrescriptionSummaryPort.PrescriptionSummary;
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
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SendGroupDoseNotificationService {

    private static final DateTimeFormatter LABEL_MONTH_DAY = DateTimeFormatter.ofPattern("M월 d일");

    private final DoseLogRepository doseLogRepository;
    private final ScheduleRepository scheduleRepository;
    private final MembershipRepository membershipRepository;
    private final NotificationPersistenceService notificationPersistenceService;
    private final UserRepository userRepository;
    private final NotificationSenderPort notificationSenderPort;
    private final RecipientCachePort recipientCachePort;
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

        List<CachedRecipient> groupRecipients = loadGroupRecipients(schedule.getCareGroupId());
        if (groupRecipients.isEmpty()) {
            return;
        }
        List<Long> recipientIds = groupRecipients.stream().map(CachedRecipient::userId).toList();

        List<Notification> notifications = buildNotifications(
                doseLog, actorUserId, schedule, recipientIds);
        if (notifications.isEmpty()) {
            return;
        }

        List<Notification> saved = notificationPersistenceService.saveAll(notifications);
        dispatchAll(saved, groupRecipients);
    }

    private void markGroupNotified(DoseLog doseLog) {
        doseLog.markGroupNotified(Instant.now(clock));
        doseLogRepository.save(doseLog);
    }

    private void dispatchAll(List<Notification> saved, List<CachedRecipient> groupRecipients) {
        Map<Long, String> tokensByUserId = tokensByUserId(groupRecipients);
        List<NotificationCommand> commands = saved.stream()
                .map(n -> toCommand(n, tokensByUserId.get(n.getRecipientUserId())))
                .toList();
        List<Long> sentIds = notificationSenderPort.sendAll(commands);
        markSentAll(sentIds);
    }

    private Map<Long, String> tokensByUserId(List<CachedRecipient> groupRecipients) {
        Map<Long, String> tokens = new HashMap<>();
        groupRecipients.stream()
                .filter(recipient -> recipient.token() != null)
                .forEach(recipient -> tokens.put(recipient.userId(), recipient.token()));
        return tokens;
    }

    // 그룹 수신자+토큰 — 캐시 우선(TTL 5m), miss 시 DB 조회 후 적재 (Redis 장애 시 캐시 miss 로 DB fallback)
    private List<CachedRecipient> loadGroupRecipients(Long careGroupId) {
        if (careGroupId == null) {
            return List.of();
        }
        return recipientCachePort.get(careGroupId)
                .orElseGet(() -> loadAndCacheRecipients(careGroupId));
    }

    private List<CachedRecipient> loadAndCacheRecipients(Long careGroupId) {
        List<Long> memberIds = findGroupMembersByGroup(careGroupId);
        if (memberIds.isEmpty()) {
            return List.of();
        }
        Map<Long, String> tokens = new HashMap<>();
        userRepository.findAllByIdIn(memberIds)
                .forEach(user -> tokens.put(user.getId(), user.getExpoPushToken()));
        List<CachedRecipient> recipients = memberIds.stream()
                .map(id -> new CachedRecipient(id, tokens.get(id)))
                .toList();
        recipientCachePort.put(careGroupId, recipients);
        return recipients;
    }

    private void markSentAll(List<Long> sentNotificationIds) {
        Instant now = Instant.now(clock);
        sentNotificationIds.forEach(id -> notificationPersistenceService.markSent(id, now));
    }

    private NotificationCommand toCommand(Notification n, String token) {
        return new NotificationCommand(
                n.getId(),
                n.getRecipientUserId(),
                token,
                n.getTitle(),
                n.getBody(),
                Map.of("route", resolveRoute(n), "type", n.getType().name())
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

    // 알림 표시용 처방전 이름: ①사용자 label(non-blank) 그대로 ②없으면 'M월 D일 약봉투'
    // (카드 표시 규칙과 동일 — GetDayScheduleService.resolvePrescriptionLabels 참조. 알림은 단건이라 번호 불필요)
    private String resolvePrescriptionName(Long prescriptionId) {
        if (prescriptionId == null) {
            return null;
        }
        return prescriptionSummaryPort.findById(prescriptionId)
                .map(this::resolveLabel)
                .orElse(null);
    }

    private String resolveLabel(PrescriptionSummary summary) {
        if (isNotBlank(summary.label())) {
            return summary.label();
        }
        return summary.prescribedAt().format(LABEL_MONTH_DAY) + " 약봉투";
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
