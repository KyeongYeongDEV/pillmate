package com.pillmate.caregroup.infrastructure.persistence;

import com.pillmate.caregroup.domain.model.Membership;
import com.pillmate.caregroup.domain.model.MembershipPair;
import com.pillmate.caregroup.domain.model.MembershipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

interface MembershipJpaRepository extends JpaRepository<Membership, Long> {
    Optional<Membership> findByCareGroupIdAndUserIdAndStatus(Long careGroupId, Long userId, MembershipStatus status);
    List<Membership> findByCareGroupIdAndStatus(Long careGroupId, MembershipStatus status);
    boolean existsByCareGroupIdAndUserIdAndStatus(Long careGroupId, Long userId, MembershipStatus status);
    List<Membership> findByUserIdAndStatus(Long userId, MembershipStatus status);
    Optional<Membership> findFirstByUserIdAndStatusAndPinnedTrue(Long userId, MembershipStatus status);

    @Query("""
            SELECT (COUNT(m1) > 0) FROM Membership m1, Membership m2
            WHERE m1.careGroupId = m2.careGroupId
              AND m1.userId = :viewer AND m2.userId = :target
              AND m1.status = com.pillmate.caregroup.domain.model.MembershipStatus.ACTIVE
              AND m2.status = com.pillmate.caregroup.domain.model.MembershipStatus.ACTIVE
            """)
    boolean existsSharedGroup(@Param("viewer") Long viewer, @Param("target") Long target);

    @Query("""
            SELECT (COUNT(m1) > 0) FROM Membership m1, Membership m2
            WHERE m1.careGroupId = m2.careGroupId
              AND m1.userId = :guardian AND m1.role = 'GUARDIAN'
              AND m2.userId = :patient  AND m2.role = 'PATIENT'
              AND m1.status = com.pillmate.caregroup.domain.model.MembershipStatus.ACTIVE
              AND m2.status = com.pillmate.caregroup.domain.model.MembershipStatus.ACTIVE
            """)
    boolean existsByGuardianAndPatient(@Param("guardian") Long guardian, @Param("patient") Long patient);

    @Query("""
            SELECT DISTINCT m2.userId FROM Membership m1, Membership m2
            WHERE m1.careGroupId = m2.careGroupId
              AND m1.userId = :viewer AND m2.userId <> :viewer
              AND m1.status = com.pillmate.caregroup.domain.model.MembershipStatus.ACTIVE
              AND m2.status = com.pillmate.caregroup.domain.model.MembershipStatus.ACTIVE
            """)
    List<Long> findGroupMemberUserIds(@Param("viewer") Long viewer);

    @Query("""
            SELECT new com.pillmate.caregroup.domain.model.MembershipPair(m2.careGroupId, m2.userId)
            FROM Membership m1, Membership m2
            WHERE m1.careGroupId = m2.careGroupId
              AND m1.userId = :actorUserId AND m2.userId <> :actorUserId
              AND m1.status = com.pillmate.caregroup.domain.model.MembershipStatus.ACTIVE
              AND m2.status = com.pillmate.caregroup.domain.model.MembershipStatus.ACTIVE
            """)
    List<MembershipPair> findGroupMemberPairs(@Param("actorUserId") Long actorUserId);
}
