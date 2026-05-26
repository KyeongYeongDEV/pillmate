package com.pillmate.drug.domain.repository;

import com.pillmate.drug.domain.model.AliasSource;
import com.pillmate.drug.domain.model.DrugAlias;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface DrugAliasRepository {

    List<DrugAlias> findByAlias(String alias);

    Optional<DrugAlias> findByAliasAndItemSeq(String alias, String itemSeq);

    Optional<DrugAlias> findById(Long id);

    Page<DrugAlias> findPendingBySource(AliasSource source, Pageable pageable);

    DrugAlias save(DrugAlias alias);
}
