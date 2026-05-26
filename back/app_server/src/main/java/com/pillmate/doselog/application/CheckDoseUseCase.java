package com.pillmate.doselog.application;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.doselog.application.dto.CheckDoseRequest;
import com.pillmate.doselog.application.dto.DoseLogResponse;
import com.pillmate.doselog.domain.model.DoseLog;
import com.pillmate.doselog.domain.repository.DoseLogRepository;
import com.pillmate.activity.application.ActivityFeedAppender;
import com.pillmate.common.security.UserContext;
import com.pillmate.schedule.domain.model.Schedule;
import com.pillmate.schedule.domain.repository.ScheduleRepository;
import com.pillmate.user.domain.model.User;
import com.pillmate.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CheckDoseUseCase {

    private final DoseLogRepository doseLogRepository;
    private final ScheduleRepository scheduleRepository;
    private final UserRepository userRepository;
    private final ActivityFeedAppender activityFeedAppender;

    @Transactional
    public DoseLogResponse check(CheckDoseRequest req, Long checkedBy) {
        DoseLog doseLog = doseLogRepository.findById(req.doseLogId())
                .orElseThrow(() -> new PillmateException(ErrorCode.INVALID_REQUEST));
        verifyOwnership(doseLog.getPatientId());

        if ("TAKE".equalsIgnoreCase(req.action())) {
            doseLog.take(checkedBy);
            appendTakenActivity(doseLog);
        } else if ("SKIP".equalsIgnoreCase(req.action())) {
            doseLog.skip(checkedBy, req.skipReason());
        } else {
            throw new PillmateException(ErrorCode.INVALID_REQUEST);
        }

        return DoseLogResponse.from(doseLogRepository.save(doseLog));
    }

    private void verifyOwnership(Long patientId) {
        Long currentUser = UserContext.get();
        if (currentUser == null || !currentUser.equals(patientId)) {
            throw new PillmateException(ErrorCode.PATIENT_ACCESS_DENIED);
        }
    }

    private void appendTakenActivity(DoseLog doseLog) {
        Schedule schedule = scheduleRepository.findById(doseLog.getScheduleId()).orElse(null);
        if (schedule == null) return;
        String actorName = userRepository.findById(doseLog.getPatientId())
                .map(User::getName)
                .orElse("멤버");
        activityFeedAppender.appendTaken(doseLog.getPatientId(), schedule.getTimeOfDay(), actorName);
    }
}
