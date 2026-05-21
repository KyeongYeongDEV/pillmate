package com.pillmate.caregroup.infrastructure.persistence;

import com.pillmate.caregroup.domain.model.Membership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface MembershipJpaRepository extends JpaRepository<Membership, Long> {
    Optional<Membership> findByCareGroupIdAndUserId(Long careGroupId, Long userId);
    List<Membership> findByCareGroupId(Long careGroupId);
    boolean existsByCareGroupIdAndUserId(Long careGroupId, Long userId);
}
