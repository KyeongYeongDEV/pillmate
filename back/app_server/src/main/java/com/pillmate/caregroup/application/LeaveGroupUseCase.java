package com.pillmate.caregroup.application;

import com.pillmate.caregroup.domain.model.Membership;
import com.pillmate.caregroup.domain.repository.MembershipRepository;
import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class LeaveGroupUseCase {

    private final MembershipRepository membershipRepository;
    private final Clock clock;

    @Transactional
    public void leave(Long groupId, Long userId) {
        Membership membership = findActiveMembership(groupId, userId);
        membership.leave(clock);
        membershipRepository.save(membership);
    }

    private Membership findActiveMembership(Long groupId, Long userId) {
        return membershipRepository.findByCareGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new PillmateException(ErrorCode.GROUP_ACCESS_DENIED));
    }
}
