package com.pillmate.prescription.infrastructure.persistence;

import com.pillmate.prescription.application.port.ActiveMedicationPort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Repository
public class ActiveMedicationAdapter implements ActiveMedicationPort {

    @PersistenceContext
    private EntityManager em;

    @Override
    @SuppressWarnings("unchecked")
    public Set<Long> findActivePrescriptionIds(Long patientId, LocalDate today) {
        String sql = "SELECT DISTINCT s.prescription_id FROM schedules s " +
                     "WHERE s.patient_id = :patientId AND s.active = true " +
                     "AND s.prescription_id IS NOT NULL " +
                     "AND s.start_date <= :today " +
                     "AND (s.end_date IS NULL OR s.end_date >= :today)";
        List<Object> rows = em.createNativeQuery(sql)
                .setParameter("patientId", patientId)
                .setParameter("today", today)
                .getResultList();
        Set<Long> ids = new LinkedHashSet<>();
        for (Object row : rows) {
            ids.add(((Number) row).longValue());
        }
        return ids;
    }
}
