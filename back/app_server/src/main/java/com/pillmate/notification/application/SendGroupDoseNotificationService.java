package com.pillmate.notification.application;

import com.pillmate.caregroup.application.MedicationShareService;
import com.pillmate.caregroup.domain.model.Membership;
import com.pillmate.caregroup.domain.repository.MembershipRepository;
import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.common.security.CareGroupGuard;
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
    private final CareGroupGuard careGroupGuard;
    private final MedicationShareService medicationShareService;
    private final Clock clock;

    // 백그라운드 폴러(NotifyDueGroupDosesService) 전용 — doseLogId 는 폴러 자신의 쿼리 결과이므로
    // 호출자 신원 검증 불필요(요청 컨텍스트 자체가 없음). 외부 HTTP 진입점은 반드시 sendForCaller 사용.
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

    // 외부 HTTP 진입점(NotificationController) 전용 — doseLogId 는 사용자 입력(IDOR 가능)이므로
    // 발송 전 호출자가 실제로 이 doseLog 의 케어그룹 소속인지 검증한다. 실패 시 markGroupNotified 도
    // 일어나지 않아야 하므로(DoS 방지) send() 호출보다 먼저 확인한다.
    public void sendForCaller(Long doseLogId, Long callerUserId) {
        requireCallerAuthorized(doseLogId, callerUserId);
        send(doseLogId, callerUserId);
    }

    private void requireCallerAuthorized(Long doseLogId, Long callerUserId) {
        DoseLog doseLog = findDoseLog(doseLogId);
        Schedule schedule = findSchedule(doseLog.getScheduleId());
        Long careGroupId = schedule.getCareGroupId();
        if (careGroupId != null) {
            careGroupGuard.requireAccessible(careGroupId);
            return;
        }
        requireCallerIsPatient(doseLog, callerUserId);
    }

    private void requireCallerIsPatient(DoseLog doseLog, Long callerUserId) {
        if (callerUserId == null || !callerUserId.equals(doseLog.getPatientId())) {
            throw new PillmateException(ErrorCode.GROUP_ACCESS_DENIED);
        }
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
        Long careGroupId = schedule.getCareGroupId();
        Long patientId = doseLog.getPatientId();
        PrescriptionSummary summary = resolvePrescriptionSummary(prescriptionId);
        String actorName = resolveActorName(actorUserId);
        String groupName = resolveGroupName(careGroupId);
        return recipientIds.stream()
                .filter(id -> !id.equals(actorUserId))
                .filter(id -> !id.equals(patientId))
                .map(recipientId -> buildOne(
                        isMissed, recipientId, actorUserId, careGroupId, doseLog.getId(), prescriptionId,
                        resolveVisibleLabel(summary, careGroupId, patientId, recipientId), actorName, groupName))
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

    private PrescriptionSummary resolvePrescriptionSummary(Long prescriptionId) {
        if (prescriptionId == null) {
            return null;
        }
        return prescriptionSummaryPort.findById(prescriptionId).orElse(null);
    }

    // 알림 표시용 처방전 이름: ①사용자 label(non-blank) 그대로 ②없으면 'M월 D일 약봉투'
    // (카드 표시 규칙과 동일 — GetDayScheduleService.resolvePrescriptionLabels 참조. 알림은 단건이라 번호 불필요)
    // L2(알약 정보) 는 2축 AND — 약봉투축(sharedWithGroup)이 꺼져 있으면 즉시 차단하고,
    // 켜져 있어도 구성원축(owner→recipient grant)이 없는 수신자에게는 라벨을 노출하지 않는다.
    // 수신자마다 구성원축이 다르므로 그룹 알림(푸시·목록)이 /schedules/day 마스킹을 우회하는
    // 유출 경로가 되지 않도록 수신자별로 판정한다.
    private String resolveVisibleLabel(PrescriptionSummary summary, Long careGroupId, Long patientId, Long recipientId) {
        if (summary == null || !summary.sharedWithGroup()) {
            return null;
        }
        if (!medicationShareService.isMemberGranted(careGroupId, patientId, recipientId)) {
            return null;
        }
        return resolveLabel(summary);
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
