package com.pillmate.drug.infrastructure.persistence;

import com.pillmate.drug.domain.model.DrugAlias;
import com.pillmate.drug.domain.repository.DrugAliasRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
class DrugAliasRepositoryImpl implements DrugAliasRepository {

    private final DrugAliasJpaRepository jpaRepository;

    @Override
    public List<DrugAlias> findByAlias(String alias) {
        return jpaRepository.findByAlias(alias);
    }

    @Override
    public DrugAlias save(DrugAlias alias) {
        return jpaRepository.save(alias);
    }
}
