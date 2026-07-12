package com.pillmate.doselog.application;

import com.pillmate.doselog.domain.model.DoseLog;
import com.pillmate.doselog.domain.repository.DoseLogRepository;
import com.pillmate.doselog.domain.service.DoseLogSchedulePolicy;
import com.pillmate.prescription.application.port.DoseLogBackfillPort;
import com.pillmate.schedule.domain.model.Schedule;
import com.pillmate.schedule.domain.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GenerateDailyDoseLogsService implements GenerateDailyDoseLogsUseCase, DoseLogBackfillPort {

    private final ScheduleRepository scheduleRepository;
    private final DoseLogRepository doseLogRepository;
    private final DoseLogSchedulePolicy policy;

    @Transactional
    @Override
    public int generate(LocalDate date) {
        List<Schedule> activeSchedules = scheduleRepository.findAllActiveOn(date);
        int created = 0;
        for (Schedule schedule : activeSchedules) {
            created += createIfAbsent(schedule.getId(), schedule.getPatientId(), schedule.getCustomTime(), date);
        }
        log.info("DailyDoseLogGen date={} created={} schedules={}", date, created, activeSchedules.size());
        return created;
    }

    // 처방전 등록 직후 오늘자 즉시 백필 — 야간 배치와 동일한 멱등 조건(createIfAbsent) 재사용.
    // 활성 시작일이 미래인 슬롯은 스킵(기존 야간 배치가 시작일 도래 시 처리).
    @Transactional
    @Override
    public int backfillToday(Long patientId, List<BackfillSlot> slots, LocalDate today) {
        int created = 0;
        for (BackfillSlot slot : slots) {
            if (!isActiveOn(slot, today)) {
                continue;
            }
            created += createIfAbsent(slot.scheduleId(), patientId, slot.customTime(), today);
        }
        log.info("DoseLogBackfill patientId={} today={} created={} slots={}",
                patientId, today, created, slots.size());
        return created;
    }

    private boolean isActiveOn(BackfillSlot slot, LocalDate today) {
        boolean started = slot.startDate() == null || !slot.startDate().isAfter(today);
        boolean notEnded = slot.endDate() == null || !slot.endDate().isBefore(today);
        return started && notEnded;
    }

    private int createIfAbsent(Long scheduleId, Long patientId, LocalTime customTime, LocalDate date) {
        boolean existsToday = doseLogRepository.existsByScheduleIdAndScheduledAtInRange(
                scheduleId, policy.startOfDay(date), policy.startOfNextDay(date));
        if (existsToday) {
            return 0;
        }
        Instant scheduledAt = policy.scheduledAtFor(customTime, date);
        doseLogRepository.save(DoseLog.of(scheduleId, patientId, scheduledAt));
        return 1;
    }
}
