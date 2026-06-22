package com.pillmate.schedule.infrastructure.persistence;

import com.pillmate.schedule.application.port.ScheduleDayQueryPort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

@Repository
@RequiredArgsConstructor
class ScheduleDayQueryAdapter implements ScheduleDayQueryPort {

    // 처방전(약봉투) 단위 조회 — prescription_id 있으면 처방전 라벨, 없으면 drugs.name fallback.
    // LEFT JOIN 으로 레거시 per-drug seed(prescription_id IS NULL) 도 day view 에 포함.
    private static final String DAY_SQL = """
            SELECT s.id              AS schedule_id,
                   s.custom_time,
                   s.prescription_id,
                   p.prescribed_at,
                   agg.drug_names,
                   agg.pill_colors,
                   dl.id             AS dose_log_id,
                   dl.status         AS dose_status,
                   d_single.name     AS single_drug_name
            FROM schedules s
            LEFT JOIN prescriptions p ON p.id = s.prescription_id
            LEFT JOIN LATERAL (
                SELECT array_agg(COALESCE(d.name, pd.name_raw) ORDER BY pd.id) AS drug_names,
                       array_agg(d.color_class ORDER BY pd.id)                 AS pill_colors
                FROM prescribed_drugs pd
                LEFT JOIN drugs d ON d.id = pd.drug_id
                WHERE pd.prescription_id = s.prescription_id
            ) agg ON TRUE
            LEFT JOIN drugs d_single ON d_single.id = s.drug_id
            LEFT JOIN dose_logs dl
                   ON dl.schedule_id = s.id
                  AND (dl.scheduled_at AT TIME ZONE 'Asia/Seoul')::date = :date
            WHERE s.patient_id = :pid
              AND s.active = TRUE
              AND s.start_date <= :date
              AND (s.end_date IS NULL OR s.end_date >= :date)
            ORDER BY s.custom_time ASC, s.prescription_id ASC NULLS LAST, s.id ASC
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
        Long scheduleId        = toLong(row.get("schedule_id"));
        LocalTime customTime   = toLocalTime(row.get("custom_time"));
        Long prescriptionId    = toLong(row.get("prescription_id"));
        LocalDate prescribedAt = toLocalDate(row.get("prescribed_at"));
        List<String> drugNames  = toStringList(row.get("drug_names"));
        List<String> pillColors = toStringList(row.get("pill_colors"));
        Long doseLogId         = toLong(row.get("dose_log_id"));
        String status          = (String) row.get("dose_status");
        String singleDrugName  = (String) row.get("single_drug_name");
        return new DayScheduleProjection(
                scheduleId, customTime, prescriptionId, prescribedAt,
                drugNames, pillColors, doseLogId, status, singleDrugName);
    }

    private Long toLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private LocalDate toLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        return ((Date) value).toLocalDate();
    }

    private LocalTime toLocalTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalTime localTime) {
            return localTime;
        }
        return ((Time) value).toLocalTime();
    }

    private List<String> toStringList(Object value) {
        if (value == null) {
            return List.of();
        }
        Object[] elements = unwrapArray(value);
        if (elements == null) {
            return List.of();
        }
        return Arrays.stream(elements)
                .map(element -> element != null ? element.toString() : null)
                .toList();
    }

    private Object[] unwrapArray(Object value) {
        if (value instanceof Object[] array) {
            return array;
        }
        if (value instanceof Array sqlArray) {
            try {
                return (Object[]) sqlArray.getArray();
            } catch (SQLException e) {
                throw new IllegalStateException("day schedule 배열 변환 실패", e);
            }
        }
        return null;
    }
}
