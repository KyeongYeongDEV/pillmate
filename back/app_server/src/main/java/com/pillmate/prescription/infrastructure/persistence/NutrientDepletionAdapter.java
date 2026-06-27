package com.pillmate.prescription.infrastructure.persistence;

import com.pillmate.prescription.application.dto.NutrientNote;
import com.pillmate.prescription.application.port.NutrientDepletionPort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
class NutrientDepletionAdapter implements NutrientDepletionPort {

    private static final String FIND_SQL =
            "SELECT d.id AS drug_id, dnd.nutrient, dnd.advice, dnd.source " +
            "FROM drugs d " +
            "JOIN drug_nutrient_depletion dnd " +
            "  ON LOWER(d.ingredient) LIKE '%' || LOWER(dnd.ingredient) || '%' " +
            "WHERE d.id IN (:drugIds) AND d.status = 'ACTIVE'";

    private final EntityManager entityManager;

    @Override
    @SuppressWarnings("unchecked")
    public Map<Long, List<NutrientNote>> findByDrugIds(Collection<Long> drugIds) {
        if (drugIds.isEmpty()) return Map.of();
        List<Tuple> rows = entityManager.createNativeQuery(FIND_SQL, Tuple.class)
                .setParameter("drugIds", drugIds)
                .getResultList();
        Map<Long, List<NutrientNote>> result = new HashMap<>();
        for (Tuple row : rows) {
            Long drugId = ((Number) row.get("drug_id")).longValue();
            NutrientNote note = new NutrientNote(
                    (String) row.get("nutrient"),
                    (String) row.get("advice"),
                    (String) row.get("source"));
            result.computeIfAbsent(drugId, k -> new java.util.ArrayList<>()).add(note);
        }
        return result;
    }
}
