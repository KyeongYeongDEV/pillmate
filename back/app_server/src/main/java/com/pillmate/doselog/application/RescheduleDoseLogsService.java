package com.pillmate.doselog.application;

import com.pillmate.doselog.domain.model.DoseLog;
import com.pillmate.doselog.domain.model.DoseStatus;
import com.pillmate.doselog.domain.repository.DoseLogRepository;
import com.pillmate.doselog.domain.service.DoseLogSchedulePolicy;
import com.pillmate.schedule.application.port.RescheduleDoseLogsPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RescheduleDoseLogsService implements RescheduleDoseLogsPort {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final DoseLogRepository doseLogRepository;
    private final DoseLogSchedulePolicy doseLogSchedulePolicy;

    @Override
    @Transactional
    public void rescheduleFuturePending(Long scheduleId, LocalTime newTime, LocalDate fromDate) {
        Instant from = doseLogSchedulePolicy.startOfDay(fromDate);
        List<DoseLog> pendingLogs = doseLogRepository.findByScheduleIdAndStatusFrom(
                scheduleId, DoseStatus.PENDING, from);
        for (DoseLog log : pendingLogs) {
            LocalDate logDate = log.getScheduledAt().atZone(KST).toLocalDate();
            log.reschedule(doseLogSchedulePolicy.scheduledAtFor(newTime, logDate));
            doseLogRepository.save(log);
        }
    }
}
