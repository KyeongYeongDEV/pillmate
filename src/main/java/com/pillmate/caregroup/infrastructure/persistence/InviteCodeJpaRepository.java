package com.pillmate.caregroup.infrastructure.persistence;

import com.pillmate.caregroup.domain.model.InviteCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

interface InviteCodeJpaRepository extends JpaRepository<InviteCode, Long> {

    @Query("SELECT i FROM InviteCode i WHERE i.code = :code AND i.usedAt IS NULL AND i.expiresAt > CURRENT_TIMESTAMP")
    Optional<InviteCode> findUsableByCode(@Param("code") String code);
}
