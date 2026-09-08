package com.pillmate.schedule.infrastructure.persistence;

import com.pillmate.schedule.application.port.ScheduleMonthQueryPort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Repository
@RequiredArgsConstructor
class ScheduleMonthQueryAdapter implements ScheduleMonthQueryPort {

    // 오늘까지(date<=:today)는 dose_logs 실측 기반(#134 회귀 방지 — 상태 있는 유일 소스).
    // 내일부터(date>:today)는 아직 dose_log 가 생성되지 않았으므로(#134) schedules 활성 여부로
    // totalCount 를 합성하고 takenCount=0 으로 둔다 — 등록 즉시 캘린더 점이 뜨도록.
    // 두 구간은 :today 경계로 서로 배타적이라 UNION ALL 로 합쳐도 날짜 중복이 없다.
    private static final String MONTH_SQL = """
            WITH bounds AS (
                SELECT (:fromTs AT TIME ZONE 'Asia/Seoul')::date                    AS month_start,
                       ((:toTs AT TIME ZONE 'Asia/Seoul') - INTERVAL '1 day')::date AS month_end
            ),
            past_and_today AS (
                SELECT (dl.scheduled_at AT TIME ZONE 'Asia/Seoul')::date AS dose_date,
                       COUNT(*)                                          AS total_count,
                       COUNT(*) FILTER (WHERE dl.status = 'TAKEN')       AS taken_count
                FROM dose_logs dl
                WHERE dl.patient_id = :pid
                  AND dl.scheduled_at >= :fromTs
                  AND dl.scheduled_at < :toTs
                  AND (dl.scheduled_at AT TIME ZONE 'Asia/Seoul')::date <= (:today)::date
                GROUP BY dose_date
            ),
            future_dates AS (
                SELECT generate_series(
                           GREATEST(bounds.month_start, (:today)::date + 1)::timestamp,
                           bounds.month_end::timestamp,
                           INTERVAL '1 day'
                       )::date AS dose_date
                FROM bounds
            ),
            future_counts AS (
                SELECT fd.dose_date,
                       COUNT(s.id)::int AS total_count,
                       0                AS taken_count
                FROM future_dates fd
                JOIN schedules s
                  ON s.patient_id = :pid
                 AND s.active = TRUE
                 AND s.start_date <= fd.dose_date
                 AND (s.end_date IS NULL OR s.end_date >= fd.dose_date)
                GROUP BY fd.dose_date
            )
            SELECT dose_date, total_count, taken_count FROM past_and_today
            UNION ALL
            SELECT dose_date, total_count, taken_count FROM future_counts
            ORDER BY dose_date
            """;

    private final EntityManager entityManager;

    @Override
    @SuppressWarnings("unchecked")
    public List<DayDoseCount> findDailyDoseCounts(Long patientId, Instant fromInclusive, Instant toExclusive, LocalDate today) {
        List<Tuple> rows = entityManager.createNativeQuery(MONTH_SQL, Tuple.class)
                .setParameter("pid", patientId)
                .setParameter("fromTs", fromInclusive)
                .setParameter("toTs", toExclusive)
                .setParameter("today", today)
                .getResultList();

        return rows.stream()
                .map(this::toDayDoseCount)
                .toList();
    }

    private DayDoseCount toDayDoseCount(Tuple row) {
        Date doseDate = (Date) row.get("dose_date");
        int totalCount = ((Number) row.get("total_count")).intValue();
        int takenCount = ((Number) row.get("taken_count")).intValue();
        return new DayDoseCount(doseDate.toLocalDate(), totalCount, takenCount);
    }
}
