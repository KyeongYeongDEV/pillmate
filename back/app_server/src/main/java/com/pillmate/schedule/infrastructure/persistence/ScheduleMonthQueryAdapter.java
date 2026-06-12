package com.pillmate.schedule.infrastructure.persistence;

import com.pillmate.schedule.application.port.ScheduleMonthQueryPort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.Instant;
import java.util.List;

@Repository
@RequiredArgsConstructor
class ScheduleMonthQueryAdapter implements ScheduleMonthQueryPort {

    // scheduled_at 범위 조건: 월 파티션 프루닝 (#88 UTC 버그 재발 주의 — 날짜 집계는 KST 변환 후)
    private static final String MONTH_SQL = """
            SELECT (dl.scheduled_at AT TIME ZONE 'Asia/Seoul')::date AS dose_date,
                   COUNT(*)                                          AS total_count,
                   COUNT(*) FILTER (WHERE dl.status = 'TAKEN')       AS taken_count
            FROM dose_logs dl
            WHERE dl.patient_id = :pid
              AND dl.scheduled_at >= :fromTs
              AND dl.scheduled_at < :toTs
            GROUP BY dose_date
            ORDER BY dose_date
            """;

    private final EntityManager entityManager;

    @Override
    @SuppressWarnings("unchecked")
    public List<DayDoseCount> findDailyDoseCounts(Long patientId, Instant fromInclusive, Instant toExclusive) {
        List<Tuple> rows = entityManager.createNativeQuery(MONTH_SQL, Tuple.class)
                .setParameter("pid", patientId)
                .setParameter("fromTs", fromInclusive)
                .setParameter("toTs", toExclusive)
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
