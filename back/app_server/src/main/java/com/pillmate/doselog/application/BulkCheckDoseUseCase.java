package com.pillmate.doselog.application;

import com.pillmate.activity.application.ActivityFeedAppender;
import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.doselog.application.dto.BulkCheckDoseRequest;
import com.pillmate.doselog.application.dto.DoseLogResponse;
import com.pillmate.doselog.domain.event.DoseCheckCanceled;
import com.pillmate.doselog.domain.model.DoseLog;
import com.pillmate.doselog.domain.model.DoseStatus;
import com.pillmate.doselog.domain.repository.DoseLogRepository;
import com.pillmate.schedule.domain.model.Schedule;
import com.pillmate.schedule.domain.repository.ScheduleRepository;
import com.pillmate.user.domain.model.User;
import com.pillmate.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BulkCheckDoseUseCase {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final DoseLogRepository doseLogRepository;
    private final ScheduleRepository scheduleRepository;
    private final UserRepository userRepository;
    private final ActivityFeedAppender activityFeedAppender;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public List<DoseLogResponse> bulkCheck(BulkCheckDoseRequest req, Long checkedBy) {
        List<DoseLog> doseLogs = doseLogRepository.findAllByIdIn(req.doseLogIds());
        if (doseLogs.isEmpty()) {
            throw new PillmateException(ErrorCode.INVALID_REQUEST);
        }
        verifyAllOwnership(doseLogs, checkedBy);

        boolean anyTransition = applyToAll(doseLogs, req, checkedBy);
        List<DoseLog> saved = doseLogRepository.saveAll(doseLogs);

        if (anyTransition) {
            emitSlotSideEffects(doseLogs.get(0), req.action(), checkedBy);
        }
        return saved.stream().map(DoseLogResponse::from).toList();
    }

    private void verifyAllOwnership(List<DoseLog> doseLogs, Long checkedBy) {
        boolean allOwned = doseLogs.stream().allMatch(dl -> dl.getPatientId().equals(checkedBy));
        if (!allOwned) {
            throw new PillmateException(ErrorCode.PATIENT_ACCESS_DENIED);
        }
    }

    private boolean applyToAll(List<DoseLog> doseLogs, BulkCheckDoseRequest req, Long checkedBy) {
        boolean any = false;
        for (DoseLog doseLog : doseLogs) {
            any |= applyOne(doseLog, req.action(), req.skipReason(), checkedBy);
        }
        return any;
    }

    private boolean applyOne(DoseLog doseLog, String action, String skipReason, Long checkedBy) {
        if ("TAKE".equalsIgnoreCase(action)) {
            boolean transition = doseLog.getStatus() != DoseStatus.TAKEN;
            doseLog.take(checkedBy);
            return transition;
        }
        if ("SKIP".equalsIgnoreCase(action)) {
            boolean transition = doseLog.getStatus() != DoseStatus.SKIPPED;
            doseLog.skip(checkedBy, skipReason);
            return transition;
        }
        if ("CANCEL".equalsIgnoreCase(action)) {
            return doseLog.cancel();
        }
        throw new PillmateException(ErrorCode.INVALID_REQUEST);
    }

    // 슬롯 단위 — 대표 doseLog 로 ActivityFeed/이벤트를 1번만 발화 (N행 중복 해소의 근본 fix)
    private void emitSlotSideEffects(DoseLog representative, String action, Long checkedBy) {
        if ("CANCEL".equalsIgnoreCase(action)) {
            eventPublisher.publishEvent(new DoseCheckCanceled(
                    representative.getId(), checkedBy, representative.getScheduleId()));
            appendSlotActivity(representative, false);
        } else if ("TAKE".equalsIgnoreCase(action)) {
            appendSlotActivity(representative, true);
        }
    }

    private void appendSlotActivity(DoseLog representative, boolean taken) {
        Schedule schedule = scheduleRepository.findById(representative.getScheduleId()).orElse(null);
        if (schedule == null) {
            return;
        }
        String actorName = userRepository.findById(representative.getPatientId())
                .map(User::getName)
                .orElse("멤버");
        String timeLabel = schedule.getCustomTime() != null ? schedule.getCustomTime().format(HH_MM) : "";
        if (taken) {
            activityFeedAppender.appendTaken(representative.getPatientId(), schedule.getTimeOfDay(), timeLabel, actorName);
        } else {
            activityFeedAppender.appendCanceled(representative.getPatientId(), schedule.getTimeOfDay(), timeLabel, actorName);
        }
    }
}
