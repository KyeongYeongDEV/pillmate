package com.pillmate.caregroup.infrastructure.persistence;

import com.pillmate.caregroup.domain.model.Membership;
import com.pillmate.caregroup.domain.model.MembershipPair;
import com.pillmate.caregroup.domain.model.MembershipStatus;
import com.pillmate.caregroup.domain.repository.MembershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
class MembershipRepositoryImpl implements MembershipRepository {
    private final MembershipJpaRepository jpa;

    @Override public Membership save(Membership m) { return jpa.save(m); }
    @Override public Optional<Membership> findByCareGroupIdAndUserId(Long g, Long u) { return jpa.findByCareGroupIdAndUserIdAndStatus(g, u, MembershipStatus.ACTIVE); }
    @Override public List<Membership> findByCareGroupId(Long g) { return jpa.findByCareGroupIdAndStatus(g, MembershipStatus.ACTIVE); }
    @Override public boolean existsByCareGroupIdAndUserId(Long g, Long u) { return jpa.existsByCareGroupIdAndUserIdAndStatus(g, u, MembershipStatus.ACTIVE); }
    @Override public List<Membership> findByUserId(Long u) { return jpa.findByUserIdAndStatus(u, MembershipStatus.ACTIVE); }
    @Override public boolean existsSharedGroup(Long v, Long t) { return jpa.existsSharedGroup(v, t); }
    @Override public boolean existsByGuardianAndPatient(Long g, Long p) { return jpa.existsByGuardianAndPatient(g, p); }
    @Override public List<Long> findGroupMemberUserIds(Long v) { return jpa.findGroupMemberUserIds(v); }
    @Override public List<MembershipPair> findGroupMemberPairs(Long a) { return jpa.findGroupMemberPairs(a); }
    @Override public Optional<Membership> findPinnedByUserId(Long u) { return jpa.findFirstByUserIdAndStatusAndPinnedTrue(u, MembershipStatus.ACTIVE); }
    @Override public Membership saveAndFlush(Membership m) { return jpa.saveAndFlush(m); }
}
