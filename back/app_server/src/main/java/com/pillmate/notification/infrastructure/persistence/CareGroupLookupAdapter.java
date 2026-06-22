package com.pillmate.notification.infrastructure.persistence;

import com.pillmate.caregroup.domain.repository.CareGroupRepository;
import com.pillmate.notification.application.port.CareGroupLookupPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
class CareGroupLookupAdapter implements CareGroupLookupPort {

    private final CareGroupRepository careGroupRepository;

    @Override
    public Optional<String> findNameById(Long careGroupId) {
        if (careGroupId == null) return Optional.empty();
        return careGroupRepository.findById(careGroupId).map(cg -> cg.getName());
    }
}
