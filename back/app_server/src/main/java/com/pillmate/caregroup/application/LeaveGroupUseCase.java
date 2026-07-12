package com.pillmate.caregroup.application;

import com.pillmate.caregroup.domain.model.Membership;
import com.pillmate.caregroup.domain.repository.MembershipRepository;
import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.notification.application.port.RecipientCachePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class LeaveGroupUseCase {

    private final MembershipRepository membershipRepository;
    private final RecipientCachePort recipientCachePort;
    private final Clock clock;

    @Transactional
    public void leave(Long groupId, Long userId) {
        leaveMembership(findActiveMembership(groupId, userId));
    }

    // 회원탈퇴 시 본인 모든 활성 그룹에서 soft 탈퇴 (도메인 leave 경유 + recipient 캐시 evict)
    @Transactional
    public void leaveAll(Long userId) {
        membershipRepository.findByUserId(userId).stream()
                .filter(Membership::isActive)
                .forEach(this::leaveMembership);
    }

    private void leaveMembership(Membership membership) {
        membership.leave(clock);
        membershipRepository.save(membership);
        recipientCachePort.evict(membership.getCareGroupId());
    }

    private Membership findActiveMembership(Long groupId, Long userId) {
        return membershipRepository.findByCareGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new PillmateException(ErrorCode.GROUP_ACCESS_DENIED));
    }
}
