package com.pillmate.prescription.infrastructure.persistence;

import com.pillmate.prescription.application.port.DrugLookupPort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
class DrugLookupAdapter implements DrugLookupPort {

    private static final String LOOKUP_SQL =
            "SELECT id AS drug_id, kd_code, name, item_image FROM drugs " +
            "WHERE kd_code = :kdCode AND status = 'ACTIVE'";

    private static final String LOOKUP_BY_ID_SQL =
            "SELECT id AS drug_id, kd_code, name, item_image FROM drugs " +
            "WHERE id = :drugId AND status = 'ACTIVE'";

    private final EntityManager entityManager;

    @Override
    public Optional<DrugSummary> findByKdCode(String kdCode) {
        try {
            Tuple row = (Tuple) entityManager.createNativeQuery(LOOKUP_SQL, Tuple.class)
                    .setParameter("kdCode", kdCode)
                    .getSingleResult();
            return Optional.of(toDrugSummary(row));
        } catch (NoResultException ex) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<DrugSummary> findById(Long drugId) {
        try {
            Tuple row = (Tuple) entityManager.createNativeQuery(LOOKUP_BY_ID_SQL, Tuple.class)
                    .setParameter("drugId", drugId)
                    .getSingleResult();
            return Optional.of(toDrugSummary(row));
        } catch (NoResultException ex) {
            return Optional.empty();
        }
    }

    private DrugSummary toDrugSummary(Tuple row) {
        Number drugId = (Number) row.get("drug_id");
        String code = (String) row.get("kd_code");
        String name = (String) row.get("name");
        String itemImage = (String) row.get("item_image");
        return new DrugSummary(drugId.longValue(), code, name, itemImage);
    }
}
