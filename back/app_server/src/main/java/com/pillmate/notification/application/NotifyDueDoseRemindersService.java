package com.pillmate.notification.application;

import com.pillmate.doselog.domain.model.DoseLog;
import com.pillmate.doselog.domain.repository.DoseLogRepository;
import com.pillmate.notification.application.port.DrugNameLookupPort;
import com.pillmate.notification.application.port.NotificationSenderPort;
import com.pillmate.notification.application.port.NotificationSenderPort.NotificationCommand;
import com.pillmate.notification.application.port.PrescriptionSummaryPort;
import com.pillmate.notification.application.port.PrescriptionSummaryPort.PrescriptionSummary;
import com.pillmate.notification.domain.model.Notification;
import com.pillmate.schedule.domain.model.Schedule;
import com.pillmate.schedule.domain.model.TimeOfDay;
import com.pillmate.schedule.domain.repository.ScheduleRepository;
import com.pillmate.user.domain.model.User;
import com.pillmate.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotifyDueDoseRemindersService implements NotifyDueDoseRemindersUseCase {

    // 과거 PENDING 행 폭주 방지 — 그룹 알림 폴러 RECENCY_WINDOW 선례
    private static final Duration RECENCY_WINDOW = Duration.ofMinutes(10);
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter LABEL_MONTH_DAY = DateTimeFormatter.ofPattern("M월 d일");
    private static final Map<LocalTime, String> TIME_OF_DAY_LABELS = Map.of(
            TimeOfDay.MORNING.defaultTime(), "아침",
            TimeOfDay.NOON.defaultTime(), "점심",
            TimeOfDay.EVENING.defaultTime(), "저녁",
            TimeOfDay.BEDTIME.defaultTime(), "취침 전");

    private final DoseLogRepository doseLogRepository;
    private final ScheduleRepository scheduleRepository;
    private final NotificationPersistenceService notificationPersistenceService;
    private final UserRepository userRepository;
    private final NotificationSenderPort notificationSenderPort;
    private final PrescriptionSummaryPort prescriptionSummaryPort;
    private final DrugNameLookupPort drugNameLookupPort;
    private final Clock clock;

    // 2026-09-27 FCM 성능개선(끼니시간 스파이크 대응) — 폴 사이클의 due 전체를 모아서
    // saveAll·sendAll(500-배치 sendEach)·markSentAll 각 1회로 처리한다. 기존엔 dose 1건마다
    // 개별 FCM 왕복(순차 1메시지)이라 실측 2.5 TPS 에 불과했음 — 배치 시 281 TPS (112배).
    @Override
    public int notifyDue() {
        Instant now = Instant.now(clock);
        List<DoseLog> due = doseLogRepository.findPendingNotRemindedBetween(now.minus(RECENCY_WINDOW), now);
        List<Notification> toSave = collectClaimedReminders(due);
        dispatchBatch(toSave);
        return due.size();
    }

    private List<Notification> collectClaimedReminders(List<DoseLog> due) {
        List<Notification> toSave = new ArrayList<>();
        for (DoseLog doseLog : due) {
            try {
                buildIfClaimed(doseLog).ifPresent(toSave::add);
            } catch (RuntimeException ex) {
                log.warn("복약 리마인더 처리 실패 doseLogId={} reason={}", doseLog.getId(), ex.getMessage());
            }
        }
        return toSave;
    }

    // 조건부 원자 클레임 선행 — 동시 복용체크(TAKEN)·타 인스턴스 선점이면 0행 → 발송 skip.
    // entity save 금지: detached merge 가 TAKEN 을 PENDING 으로 되돌리는 lost-update 원천 차단 (트리오 QA P0-1)
    private Optional<Notification> buildIfClaimed(DoseLog doseLog) {
        if (!claimReminder(doseLog)) {
            return Optional.empty();
        }
        Schedule schedule = scheduleRepository.findById(doseLog.getScheduleId()).orElse(null);
        if (schedule == null || !schedule.isActive()) {
            log.warn("복약 리마인더 스케줄 미조회/비활성 doseLogId={} scheduleId={}",
                    doseLog.getId(), doseLog.getScheduleId());
            return Optional.empty();
        }
        return Optional.of(Notification.doseReminder(
                doseLog.getPatientId(), schedule.getCareGroupId(), doseLog.getId(), buildBody(schedule)));
    }

    private boolean claimReminder(DoseLog doseLog) {
        return doseLogRepository.markRemindedIfPending(doseLog.getId(), Instant.now(clock)) == 1;
    }

    private void dispatchBatch(List<Notification> toSave) {
        if (toSave.isEmpty()) {
            return;
        }
        List<Notification> saved = notificationPersistenceService.saveAll(toSave);
        Map<Long, String> tokensByUserId = tokensByUserId(saved);
        List<NotificationCommand> commands = saved.stream()
                .map(n -> toCommand(n, tokensByUserId.get(n.getRecipientUserId())))
                .toList();
        List<Long> sentIds = notificationSenderPort.sendAll(commands);
        markSentAll(sentIds);
    }

    private Map<Long, String> tokensByUserId(List<Notification> notifications) {
        List<Long> recipientIds = notifications.stream()
                .map(Notification::getRecipientUserId)
                .distinct()
                .toList();
        Map<Long, String> tokens = new HashMap<>();
        userRepository.findAllByIdIn(recipientIds)
                .forEach(u -> tokens.put(u.getId(), u.getExpoPushToken()));
        return tokens;
    }

    private void markSentAll(List<Long> sentNotificationIds) {
        Instant now = Instant.now(clock);
        notificationPersistenceService.markSentAll(sentNotificationIds, now);
    }

    private NotificationCommand toCommand(Notification notification, String token) {
        return new NotificationCommand(
                notification.getId(),
                notification.getRecipientUserId(),
                token,
                notification.getTitle(),
                notification.getBody(),
                Map.of("route", "/home", "type", notification.getType().name(),
                        "notificationId", String.valueOf(notification.getId())));
    }

    private String buildBody(Schedule schedule) {
        return resolveTimeLabel(schedule.getCustomTime()) + " '" + resolveName(schedule) + "' 드실 시간이에요";
    }

    private String resolveTimeLabel(LocalTime customTime) {
        if (customTime == null) {
            return "지금";
        }
        String label = TIME_OF_DAY_LABELS.get(customTime);
        return label != null ? label : customTime.format(HH_MM);
    }

    // 표기 규칙: 그룹명 prefix·약 이름 나열 금지 — 약봉투 label 우선 (SendGroupDoseNotificationService 동일)
    private String resolveName(Schedule schedule) {
        if (schedule.getPrescriptionId() != null) {
            return resolvePrescriptionName(schedule.getPrescriptionId());
        }
        return drugNameLookupPort.findNameById(schedule.getDrugId()).orElse("약");
    }

    private String resolvePrescriptionName(Long prescriptionId) {
        return prescriptionSummaryPort.findById(prescriptionId)
                .map(this::resolveLabel)
                .orElse("약봉투");
    }

    private String resolveLabel(PrescriptionSummary summary) {
        if (summary.label() != null && !summary.label().isBlank()) {
            return summary.label();
        }
        return summary.prescribedAt().format(LABEL_MONTH_DAY) + " 약봉투";
    }
}
