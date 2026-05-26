package com.pillmate.drug.infrastructure.persistence;

import com.pillmate.drug.domain.model.Drug;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

interface DrugJpaRepository extends JpaRepository<Drug, Long> {

    Optional<Drug> findByKdCode(String kdCode);

    @Query(value = """
            SELECT * FROM drugs
            WHERE status = 'ACTIVE'
              AND (
                    name      ILIKE '%' || :query || '%'
                 OR main_ingr ILIKE '%' || :query || '%'
                 OR ingredient ILIKE '%' || :query || '%'
              )
            ORDER BY
              CASE WHEN name ILIKE :query THEN 0
                   WHEN name ILIKE :query || '%' THEN 1
                   ELSE 2 END,
              name
            LIMIT :limit
            """, nativeQuery = true)
    List<Drug> searchByKeyword(@Param("query") String query, @Param("limit") int limit);
}
