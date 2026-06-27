package com.pillmate.doselog.application;

import com.pillmate.doselog.domain.model.DoseLog;
import com.pillmate.doselog.domain.model.DoseStatus;
import com.pillmate.doselog.domain.repository.DoseLogRepository;
import com.pillmate.doselog.domain.service.DoseLogSchedulePolicy;
import com.pillmate.schedule.application.port.PeriodAdjustDoseLogsPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PeriodAdjustDoseLogsService implements PeriodAdjustDoseLogsPort {

    private final DoseLogRepository doseLogRepository;
    private final DoseLogSchedulePolicy policy;

    @Override
    @Transactional
    public int createLogsForRange(Long scheduleId, Long patientId, LocalTime customTime,
                                  LocalDate fromDate, LocalDate toDate) {
        int created = 0;
        for (LocalDate date = fromDate; !date.isAfter(toDate); date = date.plusDays(1)) {
            created += createIfAbsent(scheduleId, patientId, customTime, date);
        }
        return created;
    }

    @Override
    @Transactional
    public void skipPendingAfter(Long scheduleId, LocalDate cutoffDate) {
        Instant from = policy.startOfNextDay(cutoffDate);
        List<DoseLog> pendingLogs = doseLogRepository.findByScheduleIdAndStatusFrom(
                scheduleId, DoseStatus.PENDING, from);
        for (DoseLog log : pendingLogs) {
            log.cancelForPeriodChange();
            doseLogRepository.save(log);
        }
    }

    private int createIfAbsent(Long scheduleId, Long patientId, LocalTime customTime, LocalDate date) {
        if (doseLogRepository.existsByScheduleIdAndScheduledAtInRange(
                scheduleId, policy.startOfDay(date), policy.startOfNextDay(date))) {
            return 0;
        }
        Instant scheduledAt = policy.scheduledAtFor(customTime, date);
        doseLogRepository.save(DoseLog.of(scheduleId, patientId, scheduledAt));
        return 1;
    }
}
