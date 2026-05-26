package com.pillmate.caregroup.domain.repository;

import com.pillmate.caregroup.domain.model.InviteCode;

import java.util.Optional;

public interface InviteCodeRepository {
    InviteCode save(InviteCode inviteCode);
    Optional<InviteCode> findUsableByCode(String code);
    Optional<InviteCode> findByCode(String code);
}
