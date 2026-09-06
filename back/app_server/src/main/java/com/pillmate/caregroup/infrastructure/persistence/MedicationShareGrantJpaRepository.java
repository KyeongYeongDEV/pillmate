package com.pillmate.caregroup.infrastructure.persistence;

import com.pillmate.caregroup.domain.model.MedicationShareGrant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface MedicationShareGrantJpaRepository extends JpaRepository<MedicationShareGrant, Long> {
    Optional<MedicationShareGrant> findByCareGroupIdAndOwnerUserIdAndViewerUserId(
            Long careGroupId, Long ownerUserId, Long viewerUserId);

    List<MedicationShareGrant> findByCareGroupIdAndOwnerUserId(Long careGroupId, Long ownerUserId);

    boolean existsByOwnerUserIdAndViewerUserId(Long ownerUserId, Long viewerUserId);

    void deleteByCareGroupIdAndOwnerUserIdAndViewerUserId(Long careGroupId, Long ownerUserId, Long viewerUserId);
}
