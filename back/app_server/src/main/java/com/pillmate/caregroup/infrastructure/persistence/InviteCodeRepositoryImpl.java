package com.pillmate.caregroup.infrastructure.persistence;

import com.pillmate.caregroup.domain.model.InviteCode;
import com.pillmate.caregroup.domain.repository.InviteCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
class InviteCodeRepositoryImpl implements InviteCodeRepository {
    private final InviteCodeJpaRepository jpa;

    @Override public InviteCode save(InviteCode c) { return jpa.save(c); }
    @Override public Optional<InviteCode> findUsableByCode(String code) { return jpa.findUsableByCode(code); }
    @Override public Optional<InviteCode> findByCode(String code) { return jpa.findByCode(code); }
}
