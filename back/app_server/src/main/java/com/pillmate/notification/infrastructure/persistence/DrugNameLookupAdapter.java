package com.pillmate.notification.infrastructure.persistence;

import com.pillmate.notification.application.port.DrugNameLookupPort;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
class DrugNameLookupAdapter implements DrugNameLookupPort {

    // 리마인더 본문 표기용 단건 조회 — 발송 경로는 비트랜잭션이므로 native 단건 (PrescriptionSummaryAdapter 동일)
    private static final String NAME_SQL = "SELECT d.name FROM drugs d WHERE d.id = :did";

    private final EntityManager entityManager;

    @Override
    @SuppressWarnings("unchecked")
    public Optional<String> findNameById(Long drugId) {
        if (drugId == null) {
            return Optional.empty();
        }
        List<String> rows = entityManager.createNativeQuery(NAME_SQL, String.class)
                .setParameter("did", drugId)
                .getResultList();
        return rows.isEmpty() ? Optional.empty() : Optional.ofNullable(rows.get(0));
    }
}
