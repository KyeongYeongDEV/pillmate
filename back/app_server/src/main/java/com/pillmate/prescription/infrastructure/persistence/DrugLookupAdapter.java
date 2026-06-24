package com.pillmate.prescription.infrastructure.persistence;

import com.pillmate.prescription.application.port.DrugLookupPort;
import com.pillmate.prescription.application.port.FileStoragePort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
class DrugLookupAdapter implements DrugLookupPort {

    private static final String LOOKUP_BY_CODE_SQL =
            "SELECT id AS drug_id, kd_code, name, item_image, image_s3_key FROM drugs " +
            "WHERE kd_code = :kdCode AND status = 'ACTIVE'";

    private static final String LOOKUP_BY_ID_SQL =
            "SELECT id AS drug_id, kd_code, name, item_image, image_s3_key FROM drugs " +
            "WHERE id = :drugId AND status = 'ACTIVE'";

    private static final String LOOKUP_BY_IDS_SQL =
            "SELECT id AS drug_id, kd_code, name, item_image, image_s3_key FROM drugs " +
            "WHERE id IN (:drugIds) AND status = 'ACTIVE'";

    private final EntityManager entityManager;
    private final FileStoragePort fileStoragePort;

    @Override
    public Optional<DrugSummary> findByKdCode(String kdCode) {
        try {
            Tuple row = (Tuple) entityManager.createNativeQuery(LOOKUP_BY_CODE_SQL, Tuple.class)
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

    @Override
    @SuppressWarnings("unchecked")
    public Map<Long, DrugSummary> findByIds(Collection<Long> drugIds) {
        if (drugIds.isEmpty()) return Map.of();
        List<Tuple> rows = entityManager.createNativeQuery(LOOKUP_BY_IDS_SQL, Tuple.class)
                .setParameter("drugIds", drugIds)
                .getResultList();
        return rows.stream()
                .map(this::toDrugSummary)
                .collect(Collectors.toMap(DrugSummary::drugId, s -> s));
    }

    private DrugSummary toDrugSummary(Tuple row) {
        Number drugId = (Number) row.get("drug_id");
        String code = (String) row.get("kd_code");
        String name = (String) row.get("name");
        String s3Key = (String) row.get("image_s3_key");
        String itemImage = (String) row.get("item_image");
        String imageUrl = s3Key != null ? fileStoragePort.generateGetUrl(s3Key) : itemImage;
        return new DrugSummary(drugId.longValue(), code, name, imageUrl);
    }
}
