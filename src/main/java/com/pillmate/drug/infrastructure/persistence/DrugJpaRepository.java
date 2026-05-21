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
              AND tsv @@ plainto_tsquery('simple', :query)
            ORDER BY ts_rank(tsv, plainto_tsquery('simple', :query)) DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Drug> searchByTsv(@Param("query") String query, @Param("limit") int limit);
}
