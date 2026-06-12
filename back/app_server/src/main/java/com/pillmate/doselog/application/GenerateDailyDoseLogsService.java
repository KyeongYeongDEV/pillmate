package com.pillmate.doselog.application;

import com.pillmate.doselog.domain.model.DoseLog;
import com.pillmate.doselog.domain.repository.DoseLogRepository;
import com.pillmate.doselog.domain.service.DoseLogSchedulePolicy;
import com.pillmate.schedule.domain.model.Schedule;
import com.pillmate.schedule.domain.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GenerateDailyDoseLogsService implements GenerateDailyDoseLogsUseCase {

    private final ScheduleRepository scheduleRepository;
    private final DoseLogRepository doseLogRepository;
    private final DoseLogSchedulePolicy policy;

    @Transactional
    @Override
    public int generate(LocalDate date) {
        List<Schedule> activeSchedules = scheduleRepository.findAllActiveOn(date);
        int created = 0;
        for (Schedule schedule : activeSchedules) {
            created += createIfAbsent(schedule, date);
        }
        log.info("DailyDoseLogGen date={} created={} schedules={}", date, created, activeSchedules.size());
        return created;
    }

    private int createIfAbsent(Schedule schedule, LocalDate date) {
        Instant scheduledAt = policy.scheduledAtFor(schedule.getTimeOfDay(), date);
        boolean exists = doseLogRepository
                .findByScheduleIdAndScheduledAt(schedule.getId(), scheduledAt)
                .isPresent();
        if (exists) {
            return 0;
        }
        doseLogRepository.save(DoseLog.of(schedule.getId(), schedule.getPatientId(), scheduledAt));
        return 1;
    }
}
