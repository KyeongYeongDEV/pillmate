package com.pillmate.drug.domain.repository;

import com.pillmate.drug.domain.model.DrugAlias;

import java.util.List;

public interface DrugAliasRepository {

    List<DrugAlias> findByAlias(String alias);

    DrugAlias save(DrugAlias alias);
}
