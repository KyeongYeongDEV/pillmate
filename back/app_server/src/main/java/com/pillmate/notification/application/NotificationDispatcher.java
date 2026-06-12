package com.pillmate.notification.application;

import com.pillmate.caregroup.domain.model.MembershipPair;
import com.pillmate.caregroup.domain.repository.MembershipRepository;
import com.pillmate.doselog.domain.event.DoseCheckCanceled;
import com.pillmate.notification.application.port.NotificationSenderPort;
import com.pillmate.notification.application.port.NotificationSenderPort.NotificationCommand;
import com.pillmate.notification.domain.model.Notification;
import com.pillmate.prescription.domain.event.DdiCriticalDetected;
import com.pillmate.prescription.domain.event.PrescriptionRegistered;
import com.pillmate.report.domain.event.WeeklyReportGenerated;
import com.pillmate.schedule.domain.model.Schedule;
import com.pillmate.schedule.domain.model.TimeOfDay;
import com.pillmate.schedule.domain.repository.ScheduleRepository;
import com.pillmate.user.domain.model.User;
import com.pillmate.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final ScheduleRepository scheduleRepository;
    private final NotificationPersistenceService notificationPersistenceService;
    private final NotificationSenderPort notificationSenderPort;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(DdiCriticalDetected event) {
        String warningDetail = event.warnings().isEmpty() ? "" : event.warnings().get(0);
        Long selfGroupId = resolveAnyGroupId(event.userId());

        Notification n = Notification.ddiCritical(
                event.userId(), event.userId(), selfGroupId,
                event.prescriptionId(), warningDetail);

        Notification saved = notificationPersistenceService.saveAll(List.of(n)).get(0);
        String route = "/prescription/result/" + event.prescriptionId();
        dispatchOne(saved, route);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(PrescriptionRegistered event) {
        List<MembershipPair> pairs = membershipRepository.findGroupMemberPairs(event.actorUserId());

        List<Notification> notifications = pairs.stream()
                .map(pair -> Notification.prescriptionNew(
                        pair.memberId(), event.actorUserId(), pair.groupId(), event.prescriptionId()))
                .toList();

        if (notifications.isEmpty()) {
            return;
        }

        List<Notification> saved = notificationPersistenceService.saveAll(notifications);
        saved.forEach(n -> dispatchOne(n, "/group/" + n.getCareGroupId() + "/prescription/" + event.prescriptionId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(WeeklyReportGenerated event) {
        List<MembershipPair> pairs = membershipRepository.findGroupMemberPairs(event.actorUserId());

        List<Notification> notifications = pairs.stream()
                .map(pair -> Notification.weeklyReport(
                        pair.memberId(), event.actorUserId(), pair.groupId(), event.reportId()))
                .toList();

        if (notifications.isEmpty()) {
            return;
        }

        List<Notification> saved = notificationPersistenceService.saveAll(notifications);
        saved.forEach(n -> dispatchOne(n, "/group/" + n.getCareGroupId() + "/report/weekly"));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(DoseCheckCanceled event) {
        Schedule schedule = scheduleRepository.findById(event.scheduleId()).orElse(null);
        if (schedule == null) {
            log.warn("DoseCheckCanceled schedule 미조회 scheduleId={}", event.scheduleId());
            return;
        }

        List<Notification> notifications = buildCanceledNotifications(event, schedule);
        if (notifications.isEmpty()) {
            return;
        }

        List<Notification> saved = notificationPersistenceService.saveAll(notifications);
        saved.forEach(n -> dispatchOne(n, "/group/" + n.getCareGroupId()));
    }

    private List<Notification> buildCanceledNotifications(DoseCheckCanceled event, Schedule schedule) {
        String actorName = resolveUserName(event.actorUserId());
        String timeLabel = toKoreanTimeLabel(schedule.getTimeOfDay());
        return membershipRepository.findGroupMemberUserIds(event.actorUserId()).stream()
                .filter(id -> !id.equals(event.actorUserId()))
                .map(recipientId -> Notification.doseCanceled(
                        recipientId, event.actorUserId(), schedule.getCareGroupId(),
                        event.doseLogId(), actorName, timeLabel))
                .toList();
    }

    private String resolveUserName(Long userId) {
        return userRepository.findById(userId)
                .map(User::getName)
                .orElse("멤버");
    }

    private String toKoreanTimeLabel(TimeOfDay timeOfDay) {
        return switch (timeOfDay) {
            case MORNING -> "아침";
            case NOON -> "점심";
            case EVENING -> "저녁";
            case BEDTIME -> "취침 전";
        };
    }

    private void dispatchOne(Notification notification, String route) {
        String token = lookupToken(notification.getRecipientUserId());
        try {
            notificationSenderPort.send(toCommand(notification, token, route));
            notificationPersistenceService.markSent(notification.getId(), Instant.now());
        } catch (Exception e) {
            log.warn("푸시 발송 실패 notificationId={} type={} reason={}",
                    notification.getId(), notification.getType(), e.getMessage());
        }
    }

    private String lookupToken(Long userId) {
        return userRepository.findById(userId)
                .map(User::getExpoPushToken)
                .orElse(null);
    }

    private Long resolveAnyGroupId(Long userId) {
        return membershipRepository.findByUserId(userId).stream()
                .findFirst()
                .map(m -> m.getCareGroupId())
                .orElse(0L);
    }

    private NotificationCommand toCommand(Notification n, String token, String route) {
        Map<String, String> data = new HashMap<>();
        data.put("route", route);
        data.put("groupId", String.valueOf(n.getCareGroupId()));
        return new NotificationCommand(
                n.getId(),
                n.getRecipientUserId(),
                token,
                n.getTitle(),
                n.getBody(),
                data
        );
    }
}
