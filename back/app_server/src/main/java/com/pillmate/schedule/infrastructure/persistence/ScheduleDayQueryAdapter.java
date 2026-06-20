package com.pillmate.schedule.infrastructure.persistence;

import com.pillmate.schedule.application.port.ScheduleDayQueryPort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
class ScheduleDayQueryAdapter implements ScheduleDayQueryPort {

    private static final String DAY_SQL = """
            SELECT s.id          AS schedule_id,
                   s.time_of_day,
                   s.custom_time,
                   d.name        AS drug_name,
                   d.color_class AS pill_color,
                   dl.id         AS dose_log_id,
                   dl.status     AS dose_status
            FROM schedules s
            LEFT JOIN drugs d ON d.id = s.drug_id
            LEFT JOIN dose_logs dl
                   ON dl.schedule_id = s.id
                  AND (dl.scheduled_at AT TIME ZONE 'Asia/Seoul')::date = :date
            WHERE s.patient_id = :pid
              AND s.active = TRUE
              AND s.start_date <= :date
              AND (s.end_date IS NULL OR s.end_date >= :date)
            ORDER BY s.custom_time ASC, s.id ASC
            """;

    private final EntityManager entityManager;

    @Override
    @SuppressWarnings("unchecked")
    public List<DayScheduleProjection> findByPatientAndDate(Long patientId, LocalDate date) {
        List<Tuple> rows = entityManager.createNativeQuery(DAY_SQL, Tuple.class)
                .setParameter("pid", patientId)
                .setParameter("date", date)
                .getResultList();

        return rows.stream()
                .map(this::toProjection)
                .toList();
    }

    private DayScheduleProjection toProjection(Tuple row) {
        Long scheduleId     = toLong(row.get("schedule_id"));
        String timeOfDay    = (String) row.get("time_of_day");
        LocalTime customTime = toLocalTime(row.get("custom_time"));
        String drugName     = (String) row.get("drug_name");
        String pillColor    = (String) row.get("pill_color");
        Long doseLogId      = toLong(row.get("dose_log_id"));
        String status       = (String) row.get("dose_status");
        return new DayScheduleProjection(scheduleId, timeOfDay, customTime, drugName, pillColor, doseLogId, status);
    }

    private Long toLong(Object v) {
        return v == null ? null : ((Number) v).longValue();
    }

    private LocalTime toLocalTime(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof LocalTime localTime) {
            return localTime;
        }
        return ((Time) v).toLocalTime();
    }
}
