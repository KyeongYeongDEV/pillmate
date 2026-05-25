package com.pillmate.caregroup.infrastructure.persistence;

import com.pillmate.caregroup.domain.model.CareGroup;
import com.pillmate.caregroup.domain.repository.CareGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
class CareGroupRepositoryImpl implements CareGroupRepository {
    private final CareGroupJpaRepository jpa;

    @Override public CareGroup save(CareGroup g) { return jpa.save(g); }
    @Override public Optional<CareGroup> findById(Long id) { return jpa.findById(id); }
    @Override public List<CareGroup> findAllById(List<Long> ids) { return jpa.findAllById(ids); }
}
