package com.pillmate.report.infrastructure.scheduler;

import com.pillmate.report.application.GenerateWeeklyReportUseCase;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class WeeklyReportScheduler {

    private static final String ACTIVE_PATIENTS_SQL = """
            SELECT DISTINCT patient_id FROM dose_logs
            WHERE scheduled_at >= :from AND scheduled_at < :toExclusive
            """;

    private final GenerateWeeklyReportUseCase generateWeekly;
    private final EntityManager entityManager;
    private final Clock clock;

    @Scheduled(cron = "0 0 0 * * SUN", zone = "Asia/Seoul")
    public void run() {
        LocalDate weekStart = LocalDate.now(clock)
                .minusWeeks(1)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(7);
        List<Long> patients = activePatients(weekStart, weekEnd);
        log.info("WeeklyReportScheduler patients={}", patients.size());
        patients.forEach(id -> safeGenerate(id, weekStart));
    }

    @SuppressWarnings("unchecked")
    private List<Long> activePatients(LocalDate from, LocalDate to) {
        List<Number> rows = entityManager.createNativeQuery(ACTIVE_PATIENTS_SQL)
                .setParameter("from", from.atStartOfDay())
                .setParameter("toExclusive", to.atStartOfDay())
                .getResultList();
        return rows.stream().map(Number::longValue).toList();
    }

    private void safeGenerate(Long patientId, LocalDate weekStart) {
        try {
            generateWeekly.generate(patientId, weekStart);
        } catch (RuntimeException ex) {
            log.warn("Weekly report generation failed patientId={} reason={}",
                    patientId, ex.getClass().getSimpleName());
        }
    }
}
