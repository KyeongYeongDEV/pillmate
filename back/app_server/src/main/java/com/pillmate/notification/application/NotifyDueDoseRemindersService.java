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
import java.util.List;
import java.util.Map;

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

    @Override
    public int notifyDue() {
        Instant now = Instant.now(clock);
        List<DoseLog> due = doseLogRepository.findPendingNotRemindedBetween(now.minus(RECENCY_WINDOW), now);
        due.forEach(this::remindSafely);
        return due.size();
    }

    private void remindSafely(DoseLog doseLog) {
        try {
            remind(doseLog);
        } catch (RuntimeException ex) {
            log.warn("복약 리마인더 처리 실패 doseLogId={} reason={}", doseLog.getId(), ex.getMessage());
        }
    }

    // 조건부 원자 클레임 선행 — 동시 복용체크(TAKEN)·타 인스턴스 선점이면 0행 → 발송 skip.
    // entity save 금지: detached merge 가 TAKEN 을 PENDING 으로 되돌리는 lost-update 원천 차단 (트리오 QA P0-1)
    private void remind(DoseLog doseLog) {
        if (!claimReminder(doseLog)) {
            return;
        }
        Schedule schedule = scheduleRepository.findById(doseLog.getScheduleId()).orElse(null);
        if (schedule == null || !schedule.isActive()) {
            log.warn("복약 리마인더 스케줄 미조회/비활성 doseLogId={} scheduleId={}",
                    doseLog.getId(), doseLog.getScheduleId());
            return;
        }
        dispatch(doseLog, schedule);
    }

    private boolean claimReminder(DoseLog doseLog) {
        return doseLogRepository.markRemindedIfPending(doseLog.getId(), Instant.now(clock)) == 1;
    }

    private void dispatch(DoseLog doseLog, Schedule schedule) {
        Notification reminder = Notification.doseReminder(
                doseLog.getPatientId(), schedule.getCareGroupId(), doseLog.getId(), buildBody(schedule));
        Notification saved = notificationPersistenceService.saveAll(List.of(reminder)).get(0);
        List<Long> sentIds = notificationSenderPort.sendAll(List.of(toCommand(saved)));
        markSentAll(sentIds);
    }

    private void markSentAll(List<Long> sentNotificationIds) {
        Instant now = Instant.now(clock);
        sentNotificationIds.forEach(id -> notificationPersistenceService.markSent(id, now));
    }

    private NotificationCommand toCommand(Notification notification) {
        return new NotificationCommand(
                notification.getId(),
                notification.getRecipientUserId(),
                lookupToken(notification.getRecipientUserId()),
                notification.getTitle(),
                notification.getBody(),
                Map.of("route", "/home", "type", notification.getType().name()));
    }

    private String lookupToken(Long userId) {
        return userRepository.findById(userId).map(User::getExpoPushToken).orElse(null);
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
