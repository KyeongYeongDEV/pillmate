package com.pillmate.caregroup.domain.repository;

import com.pillmate.caregroup.domain.model.MedicationShareGrant;

import java.util.List;
import java.util.Optional;

public interface MedicationShareGrantRepository {
    MedicationShareGrant save(MedicationShareGrant grant);

    Optional<MedicationShareGrant> findByCareGroupIdAndOwnerUserIdAndViewerUserId(
            Long careGroupId, Long ownerUserId, Long viewerUserId);

    List<MedicationShareGrant> findByCareGroupIdAndOwnerUserId(Long careGroupId, Long ownerUserId);

    boolean existsByOwnerUserIdAndViewerUserId(Long ownerUserId, Long viewerUserId);

    void deleteByCareGroupIdAndOwnerUserIdAndViewerUserId(Long careGroupId, Long ownerUserId, Long viewerUserId);
}
