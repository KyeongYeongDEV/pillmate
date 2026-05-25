package com.pillmate.report.infrastructure.stats;

import com.pillmate.report.application.port.PrescriptionContextPort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
class PrescriptionContextAdapter implements PrescriptionContextPort {

    private static final String CARE_GROUP_SQL = """
            SELECT care_group_id FROM prescriptions
            WHERE patient_id = :patientId
            ORDER BY created_at DESC
            LIMIT 1
            """;

    private static final String ACTIVE_DRUGS_SQL = """
            SELECT DISTINCT d.kd_code, d.name, d.efficacy
            FROM prescribed_drugs pd
            JOIN prescriptions p ON p.id = pd.prescription_id
            JOIN drugs d ON d.id = pd.drug_id
            WHERE p.patient_id = :patientId
              AND pd.drug_id IS NOT NULL
              AND d.status = 'ACTIVE'
            ORDER BY d.kd_code
            LIMIT 20
            """;

    private final EntityManager entityManager;

    @Override
    public PatientContext loadContext(Long patientId) {
        Long careGroupId = findCareGroupId(patientId);
        List<DrugSummary> drugs = findActiveDrugs(patientId);
        return new PatientContext(careGroupId, drugs);
    }

    private Long findCareGroupId(Long patientId) {
        @SuppressWarnings("unchecked")
        List<Number> rows = entityManager.createNativeQuery(CARE_GROUP_SQL)
                .setParameter("patientId", patientId)
                .setMaxResults(1)
                .getResultList();
        return Optional.ofNullable(rows.isEmpty() ? null : rows.get(0))
                .map(Number::longValue)
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    private List<DrugSummary> findActiveDrugs(Long patientId) {
        List<Tuple> rows = entityManager.createNativeQuery(ACTIVE_DRUGS_SQL, Tuple.class)
                .setParameter("patientId", patientId)
                .getResultList();
        return rows.stream()
                .map(r -> new DrugSummary(
                        (String) r.get("kd_code"),
                        (String) r.get("name"),
                        truncate((String) r.get("efficacy"))))
                .toList();
    }

    private String truncate(String text) {
        if (text == null) return null;
        return text.length() > 200 ? text.substring(0, 200) : text;
    }
}
