package com.pillmate.drug.infrastructure.persistence;

import com.pillmate.drug.domain.model.AliasSource;
import com.pillmate.drug.domain.model.DrugAlias;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

interface DrugAliasJpaRepository extends JpaRepository<DrugAlias, Long> {

    List<DrugAlias> findByAlias(String alias);

    Optional<DrugAlias> findByAliasAndItemSeq(String alias, String itemSeq);

    @Query("SELECT a FROM DrugAlias a WHERE a.source = :source AND a.verified = false")
    Page<DrugAlias> findPendingBySource(@Param("source") AliasSource source, Pageable pageable);
}
