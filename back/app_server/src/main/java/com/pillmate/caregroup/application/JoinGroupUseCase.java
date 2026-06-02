package com.pillmate.caregroup.application;

import com.pillmate.caregroup.application.port.InviteCodeCachePort;
import com.pillmate.caregroup.domain.model.MemberRole;
import com.pillmate.caregroup.domain.model.Membership;
import com.pillmate.caregroup.domain.repository.MembershipRepository;
import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class JoinGroupUseCase {

    private final InviteCodeCachePort inviteCodeCachePort;
    private final MembershipRepository membershipRepository;

    @Transactional
    public Long join(String code, Long userId, MemberRole role) {
        requireJoinableRole(role);
        Long groupId = lookupGroupOrThrowExpired(code);
        requireNotAlreadyMember(groupId, userId);

        membershipRepository.save(Membership.of(groupId, userId, role, null));
        return groupId;
    }

    private void requireJoinableRole(MemberRole role) {
        if (role == MemberRole.ADMIN) {
            throw new PillmateException(ErrorCode.INVALID_REQUEST);
        }
    }

    private Long lookupGroupOrThrowExpired(String code) {
        return inviteCodeCachePort.findGroupId(code)
                .orElseThrow(() -> new PillmateException(ErrorCode.INVITE_CODE_EXPIRED_OR_INVALID));
    }

    private void requireNotAlreadyMember(Long careGroupId, Long userId) {
        if (membershipRepository.existsByCareGroupIdAndUserId(careGroupId, userId)) {
            throw new PillmateException(ErrorCode.GROUP_ALREADY_MEMBER);
        }
    }
}
