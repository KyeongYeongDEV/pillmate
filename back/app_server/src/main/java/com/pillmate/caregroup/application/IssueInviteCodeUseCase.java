package com.pillmate.caregroup.application;

import com.pillmate.caregroup.application.dto.InviteCodeResponse;
import com.pillmate.caregroup.application.port.InviteCodeCachePort;
import com.pillmate.caregroup.domain.model.InviteCode;
import com.pillmate.caregroup.domain.repository.InviteCodeRepository;
import com.pillmate.caregroup.domain.repository.MembershipRepository;
import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class IssueInviteCodeUseCase {

    private static final Duration REDIS_TTL = Duration.ofHours(24);

    private final InviteCodeRepository inviteCodeRepository;
    private final MembershipRepository membershipRepository;
    private final InviteCodeCachePort inviteCodeCachePort;

    @Transactional
    public InviteCodeResponse issue(Long groupId, Long currentUserId) {
        requireMember(groupId, currentUserId);
        InviteCode saved = inviteCodeRepository.save(InviteCode.generate(groupId, currentUserId));
        inviteCodeCachePort.put(saved.getCode(), groupId, REDIS_TTL);
        return InviteCodeResponse.from(saved);
    }

    private void requireMember(Long groupId, Long currentUserId) {
        if (!membershipRepository.existsByCareGroupIdAndUserId(groupId, currentUserId)) {
            throw new PillmateException(ErrorCode.GROUP_ACCESS_DENIED);
        }
    }
}
