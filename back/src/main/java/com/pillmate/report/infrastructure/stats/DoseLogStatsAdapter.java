package com.pillmate.report.infrastructure.stats;

import com.pillmate.report.application.port.DoseLogStatsPort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
class DoseLogStatsAdapter implements DoseLogStatsPort {

    private static final String AGGREGATE_SQL = """
            SELECT
              COUNT(*) AS total,
              COUNT(*) FILTER (WHERE status='TAKEN') AS taken,
              COUNT(*) FILTER (WHERE status='SKIPPED') AS skipped,
              COUNT(*) FILTER (WHERE status='DELAYED') AS delayed,
              COUNT(*) FILTER (WHERE status='TAKEN' AND
                  (checked_at IS NULL OR EXTRACT(EPOCH FROM (checked_at - scheduled_at))/60 <= 30)) AS on_time
            FROM dose_logs
            WHERE patient_id = :patientId
              AND scheduled_at >= :from
              AND scheduled_at < :toExclusive
            """;

    private static final String DAILY_SQL = """
            SELECT
              date_trunc('day', scheduled_at)::date AS d,
              COUNT(*) FILTER (WHERE status='TAKEN') AS taken,
              COUNT(*) AS total
            FROM dose_logs
            WHERE patient_id = :patientId
              AND scheduled_at >= :from
              AND scheduled_at < :toExclusive
            GROUP BY 1
            ORDER BY 1
            """;

    private final EntityManager entityManager;

    @Override
    public PeriodStats aggregate(Long patientId, LocalDate from, LocalDate to) {
        Tuple row = (Tuple) entityManager.createNativeQuery(AGGREGATE_SQL, Tuple.class)
                .setParameter("patientId", patientId)
                .setParameter("from", from.atStartOfDay())
                .setParameter("toExclusive", to.plusDays(1).atStartOfDay())
                .getSingleResult();
        int total = toInt(row.get("total"));
        int taken = toInt(row.get("taken"));
        int skipped = toInt(row.get("skipped"));
        int delayed = toInt(row.get("delayed"));
        int onTime = toInt(row.get("on_time"));
        return new PeriodStats(total, taken, skipped, delayed, onTime,
                0, 0, 0, 0, Map.of());
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<DailyCount> dailyCounts(Long patientId, LocalDate from, LocalDate to) {
        List<Tuple> rows = entityManager.createNativeQuery(DAILY_SQL, Tuple.class)
                .setParameter("patientId", patientId)
                .setParameter("from", from.atStartOfDay())
                .setParameter("toExclusive", to.plusDays(1).atStartOfDay())
                .getResultList();
        return rows.stream()
                .map(r -> new DailyCount(((Date) r.get("d")).toLocalDate(),
                        toInt(r.get("taken")), toInt(r.get("total"))))
                .toList();
    }

    private int toInt(Object v) {
        return v == null ? 0 : ((Number) v).intValue();
    }
}
