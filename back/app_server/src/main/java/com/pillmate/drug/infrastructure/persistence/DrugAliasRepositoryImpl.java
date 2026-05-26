package com.pillmate.drug.infrastructure.persistence;

import com.pillmate.drug.domain.model.AliasSource;
import com.pillmate.drug.domain.model.DrugAlias;
import com.pillmate.drug.domain.repository.DrugAliasRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
class DrugAliasRepositoryImpl implements DrugAliasRepository {

    private final DrugAliasJpaRepository jpaRepository;

    @Override
    public List<DrugAlias> findByAlias(String alias) {
        return jpaRepository.findByAlias(alias);
    }

    @Override
    public Optional<DrugAlias> findByAliasAndItemSeq(String alias, String itemSeq) {
        return jpaRepository.findByAliasAndItemSeq(alias, itemSeq);
    }

    @Override
    public Optional<DrugAlias> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Page<DrugAlias> findPendingBySource(AliasSource source, Pageable pageable) {
        return jpaRepository.findPendingBySource(source, pageable);
    }

    @Override
    public DrugAlias save(DrugAlias alias) {
        return jpaRepository.save(alias);
    }
}
