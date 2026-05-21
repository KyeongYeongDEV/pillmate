package com.pillmate.caregroup.domain.repository;

import com.pillmate.caregroup.domain.model.CareGroup;

import java.util.Optional;

public interface CareGroupRepository {
    CareGroup save(CareGroup group);
    Optional<CareGroup> findById(Long id);
}
