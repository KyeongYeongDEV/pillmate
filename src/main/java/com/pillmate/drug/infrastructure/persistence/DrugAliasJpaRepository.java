package com.pillmate.drug.infrastructure.persistence;

import com.pillmate.drug.domain.model.DrugAlias;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface DrugAliasJpaRepository extends JpaRepository<DrugAlias, Long> {

    List<DrugAlias> findByAlias(String alias);
}
