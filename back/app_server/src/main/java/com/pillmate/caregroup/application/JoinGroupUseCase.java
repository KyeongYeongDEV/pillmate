package com.pillmate.caregroup.application;

import com.pillmate.caregroup.domain.model.InviteCode;
import com.pillmate.caregroup.domain.model.MemberRole;
import com.pillmate.caregroup.domain.model.Membership;
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
        requireJoinableRole(role);
        InviteCode inviteCode = findCodeOrThrowInvalid(code);
        requireUsable(inviteCode);
        requireNotAlreadyMember(inviteCode.getCareGroupId(), userId);

        inviteCode.consume();
        membershipRepository.save(
                Membership.of(inviteCode.getCareGroupId(), userId, role, inviteCode.getCreatedBy()));
        return inviteCode.getCareGroupId();
    }

    private void requireJoinableRole(MemberRole role) {
        if (role == MemberRole.ADMIN) {
            throw new PillmateException(ErrorCode.INVALID_REQUEST);
        }
    }

    private InviteCode findCodeOrThrowInvalid(String code) {
        return inviteCodeRepository.findByCode(code)
                .orElseThrow(() -> new PillmateException(ErrorCode.GROUP_INVITE_CODE_INVALID));
    }

    private void requireUsable(InviteCode inviteCode) {
        if (inviteCode.isExpired()) {
            throw new PillmateException(ErrorCode.GROUP_INVITE_CODE_EXPIRED);
        }
        if (inviteCode.getUsedAt() != null) {
            throw new PillmateException(ErrorCode.GROUP_INVITE_CODE_USED);
        }
    }

    private void requireNotAlreadyMember(Long careGroupId, Long userId) {
        if (membershipRepository.existsByCareGroupIdAndUserId(careGroupId, userId)) {
            throw new PillmateException(ErrorCode.GROUP_ALREADY_MEMBER);
        }
    }
}
