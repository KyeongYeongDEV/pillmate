package com.pillmate.caregroup.application;

import com.pillmate.caregroup.application.dto.InviteCodeResponse;
import com.pillmate.caregroup.domain.model.InviteCode;
import com.pillmate.caregroup.domain.repository.InviteCodeRepository;
import com.pillmate.caregroup.domain.repository.MembershipRepository;
import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IssueInviteCodeUseCase {

    private final InviteCodeRepository inviteCodeRepository;
    private final MembershipRepository membershipRepository;

    @Transactional
    public InviteCodeResponse issue(Long groupId, Long currentUserId) {
        requireMember(groupId, currentUserId);
        InviteCode saved = inviteCodeRepository.save(InviteCode.generate(groupId, currentUserId));
        return InviteCodeResponse.from(saved);
    }

    private void requireMember(Long groupId, Long currentUserId) {
        if (!membershipRepository.existsByCareGroupIdAndUserId(groupId, currentUserId)) {
            throw new PillmateException(ErrorCode.GROUP_ACCESS_DENIED);
        }
    }
}
