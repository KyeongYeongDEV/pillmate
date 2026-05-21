package com.pillmate.caregroup.application;

import com.pillmate.caregroup.domain.model.InviteCode;
import com.pillmate.caregroup.domain.model.Membership;
import com.pillmate.caregroup.domain.model.MemberRole;
import com.pillmate.caregroup.domain.repository.InviteCodeRepository;
import com.pillmate.caregroup.domain.repository.MembershipRepository;
import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class JoinGroupUseCase {

    private final InviteCodeRepository inviteCodeRepository;
    private final MembershipRepository membershipRepository;

    @Transactional
    public Long join(String code, Long userId, MemberRole role) {
        InviteCode inviteCode = inviteCodeRepository.findUsableByCode(code)
                .orElseThrow(() -> new PillmateException(ErrorCode.GROUP_NOT_FOUND));

        if (!inviteCode.isUsable()) {
            throw new PillmateException(ErrorCode.INVALID_REQUEST);
        }
        if (membershipRepository.existsByCareGroupIdAndUserId(inviteCode.getCareGroupId(), userId)) {
            throw new PillmateException(ErrorCode.INVALID_REQUEST);
        }

        membershipRepository.save(
                Membership.of(inviteCode.getCareGroupId(), userId, role, inviteCode.getCreatedBy()));
        inviteCode.markUsed();
        return inviteCode.getCareGroupId();
    }
}
