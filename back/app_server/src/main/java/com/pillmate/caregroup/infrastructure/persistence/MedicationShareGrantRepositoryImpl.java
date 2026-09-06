package com.pillmate.caregroup.infrastructure.persistence;

import com.pillmate.caregroup.domain.model.MedicationShareGrant;
import com.pillmate.caregroup.domain.repository.MedicationShareGrantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
class MedicationShareGrantRepositoryImpl implements MedicationShareGrantRepository {

    private final MedicationShareGrantJpaRepository jpa;

    @Override
    public MedicationShareGrant save(MedicationShareGrant grant) {
        return jpa.save(grant);
    }

    @Override
    public Optional<MedicationShareGrant> findByCareGroupIdAndOwnerUserIdAndViewerUserId(
            Long careGroupId, Long ownerUserId, Long viewerUserId) {
        return jpa.findByCareGroupIdAndOwnerUserIdAndViewerUserId(careGroupId, ownerUserId, viewerUserId);
    }

    @Override
    public List<MedicationShareGrant> findByCareGroupIdAndOwnerUserId(Long careGroupId, Long ownerUserId) {
        return jpa.findByCareGroupIdAndOwnerUserId(careGroupId, ownerUserId);
    }

    @Override
    public boolean existsByCareGroupIdAndOwnerUserIdAndViewerUserId(
            Long careGroupId, Long ownerUserId, Long viewerUserId) {
        return jpa.existsByCareGroupIdAndOwnerUserIdAndViewerUserId(careGroupId, ownerUserId, viewerUserId);
    }

    @Override
    public void deleteByCareGroupIdAndOwnerUserIdAndViewerUserId(Long careGroupId, Long ownerUserId, Long viewerUserId) {
        jpa.deleteByCareGroupIdAndOwnerUserIdAndViewerUserId(careGroupId, ownerUserId, viewerUserId);
    }
}
