package com.pillmate.caregroup.infrastructure.persistence;

import com.pillmate.caregroup.domain.model.Membership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

interface MembershipJpaRepository extends JpaRepository<Membership, Long> {
    Optional<Membership> findByCareGroupIdAndUserId(Long careGroupId, Long userId);
    List<Membership> findByCareGroupId(Long careGroupId);
    boolean existsByCareGroupIdAndUserId(Long careGroupId, Long userId);
    List<Membership> findByUserId(Long userId);

    @Query("""
            SELECT (COUNT(m1) > 0) FROM Membership m1, Membership m2
            WHERE m1.careGroupId = m2.careGroupId
              AND m1.userId = :viewer AND m2.userId = :target
            """)
    boolean existsSharedGroup(@Param("viewer") Long viewer, @Param("target") Long target);
}
