package com.pillmate.notification.application;

import com.pillmate.caregroup.domain.repository.MembershipRepository;
import com.pillmate.notification.application.port.NotificationSenderPort;
import com.pillmate.notification.application.port.NotificationSenderPort.NotificationCommand;
import com.pillmate.notification.domain.model.Notification;
import com.pillmate.prescription.domain.event.DdiCriticalDetected;
import com.pillmate.prescription.domain.event.PrescriptionRegistered;
import com.pillmate.report.domain.event.WeeklyReportGenerated;
import com.pillmate.user.domain.model.User;
import com.pillmate.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
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
        List<Long> groupMemberIds = membershipRepository.findGroupMemberUserIds(event.actorUserId());
        Long selfGroupId = resolveAnyGroupId(event.actorUserId());

        List<Notification> notifications = groupMemberIds.stream()
                .filter(id -> !id.equals(event.actorUserId()))
                .map(recipientId -> Notification.prescriptionNew(
                        recipientId, event.actorUserId(), selfGroupId, event.prescriptionId()))
                .toList();

        if (notifications.isEmpty()) {
            return;
        }

        String route = "/prescription/result/" + event.prescriptionId();
        List<Notification> saved = notificationPersistenceService.saveAll(notifications);
        saved.forEach(n -> dispatchOne(n, route));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(WeeklyReportGenerated event) {
        List<Long> groupMemberIds = membershipRepository.findGroupMemberUserIds(event.actorUserId());
        Long selfGroupId = resolveAnyGroupId(event.actorUserId());

        List<Notification> notifications = groupMemberIds.stream()
                .filter(id -> !id.equals(event.actorUserId()))
                .map(recipientId -> Notification.weeklyReport(
                        recipientId, event.actorUserId(), selfGroupId, event.reportId()))
                .toList();

        if (notifications.isEmpty()) {
            return;
        }

        List<Notification> saved = notificationPersistenceService.saveAll(notifications);
        saved.forEach(n -> dispatchOne(n, "/report/weekly"));
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
        return new NotificationCommand(
                n.getId(),
                n.getRecipientUserId(),
                token,
                n.getTitle(),
                n.getBody(),
                Map.of("route", route)
        );
    }
}
