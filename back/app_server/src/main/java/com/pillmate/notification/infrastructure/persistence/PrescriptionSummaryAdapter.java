package com.pillmate.notification.infrastructure.persistence;

import com.pillmate.notification.application.port.PrescriptionSummaryPort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
class PrescriptionSummaryAdapter implements PrescriptionSummaryPort {

    // 처방전 이름 표기용 요약 — 알림 발송 경로는 비트랜잭션이므로 lazy 대신 단건 native 조회.
    private static final String SUMMARY_SQL = """
            SELECT p.prescribed_at,
                   p.label
            FROM prescriptions p
            WHERE p.id = :pid
            """;

    private final EntityManager entityManager;

    @Override
    @SuppressWarnings("unchecked")
    public Optional<PrescriptionSummary> findById(Long prescriptionId) {
        List<Tuple> rows = entityManager.createNativeQuery(SUMMARY_SQL, Tuple.class)
                .setParameter("pid", prescriptionId)
                .getResultList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(toSummary(rows.get(0)));
    }

    private PrescriptionSummary toSummary(Tuple row) {
        LocalDate prescribedAt = ((Date) row.get("prescribed_at")).toLocalDate();
        String label = (String) row.get("label");
        return new PrescriptionSummary(prescribedAt, label);
    }
}
