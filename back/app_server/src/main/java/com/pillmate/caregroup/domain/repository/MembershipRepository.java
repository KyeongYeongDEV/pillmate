package com.pillmate.caregroup.domain.repository;

import com.pillmate.caregroup.domain.model.Membership;

import java.util.List;
import java.util.Optional;

public interface MembershipRepository {
    Membership save(Membership membership);
    Optional<Membership> findByCareGroupIdAndUserId(Long careGroupId, Long userId);
    List<Membership> findByCareGroupId(Long careGroupId);
    boolean existsByCareGroupIdAndUserId(Long careGroupId, Long userId);
    List<Membership> findByUserId(Long userId);
    boolean existsSharedGroup(Long viewerUserId, Long targetUserId);
    boolean existsByGuardianAndPatient(Long guardianUserId, Long patientUserId);
    List<Long> findGroupMemberUserIds(Long viewerUserId);
    Optional<Membership> findPinnedByUserId(Long userId);
}
