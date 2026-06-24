package com.pillmate.prescription.infrastructure.persistence;

import com.pillmate.prescription.application.port.PrescriptionPeriodPort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class PrescriptionPeriodAdapter implements PrescriptionPeriodPort {

    @PersistenceContext
    private EntityManager em;

    @Override
    public Map<Long, PeriodStats> fetchStatsByPrescriptionIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        Map<Long, LocalDate[]> periodMap = fetchPeriodMap(ids);
        Map<Long, long[]> adherenceMap = fetchAdherenceMap(ids);
        return buildStatsMap(periodMap, adherenceMap);
    }

    @SuppressWarnings("unchecked")
    private Map<Long, LocalDate[]> fetchPeriodMap(List<Long> ids) {
        String sql = "SELECT s.prescription_id, MIN(s.start_date), MAX(s.end_date) " +
                     "FROM schedules s " +
                     "WHERE s.prescription_id IN :ids AND s.active = true " +
                     "GROUP BY s.prescription_id";
        List<Object[]> rows = em.createNativeQuery(sql)
                .setParameter("ids", ids)
                .getResultList();
        Map<Long, LocalDate[]> map = new HashMap<>();
        for (Object[] row : rows) {
            long prescriptionId = ((Number) row[0]).longValue();
            LocalDate start = ((java.sql.Date) row[1]).toLocalDate();
            LocalDate end = ((java.sql.Date) row[2]).toLocalDate();
            map.put(prescriptionId, new LocalDate[]{start, end});
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private Map<Long, long[]> fetchAdherenceMap(List<Long> ids) {
        String sql = "SELECT s.prescription_id, COUNT(dl.id), " +
                     "SUM(CASE WHEN dl.status = 'TAKEN' THEN 1 ELSE 0 END) " +
                     "FROM dose_logs dl " +
                     "JOIN schedules s ON dl.schedule_id = s.id " +
                     "WHERE s.prescription_id IN :ids " +
                     "GROUP BY s.prescription_id";
        List<Object[]> rows = em.createNativeQuery(sql)
                .setParameter("ids", ids)
                .getResultList();
        Map<Long, long[]> map = new HashMap<>();
        for (Object[] row : rows) {
            long prescriptionId = ((Number) row[0]).longValue();
            long totalDoses = ((Number) row[1]).longValue();
            long takenDoses = row[2] == null ? 0L : ((Number) row[2]).longValue();
            map.put(prescriptionId, new long[]{totalDoses, takenDoses});
        }
        return map;
    }

    private Map<Long, PeriodStats> buildStatsMap(Map<Long, LocalDate[]> periodMap, Map<Long, long[]> adherenceMap) {
        Map<Long, PeriodStats> result = new HashMap<>();
        for (Map.Entry<Long, LocalDate[]> entry : periodMap.entrySet()) {
            Long id = entry.getKey();
            LocalDate[] period = entry.getValue();
            long[] adherence = adherenceMap.getOrDefault(id, new long[]{0L, 0L});
            result.put(id, new PeriodStats(period[0], period[1], adherence[0], adherence[1]));
        }
        return result;
    }
}
