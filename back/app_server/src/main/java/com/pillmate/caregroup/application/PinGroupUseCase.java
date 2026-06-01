package com.pillmate.caregroup.application;

import com.pillmate.caregroup.domain.model.Membership;
import com.pillmate.caregroup.domain.repository.MembershipRepository;
import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PinGroupUseCase {

    private final MembershipRepository membershipRepository;

    @Transactional
    public void pin(Long groupId, Long userId) {
        Membership target = membershipRepository.findByCareGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new PillmateException(ErrorCode.GROUP_ACCESS_DENIED));

        unpinExistingPin(userId, groupId);

        if (!target.isPinned()) {
            target.pin();
            membershipRepository.save(target);
        }
    }

    private void unpinExistingPin(Long userId, Long targetGroupId) {
        membershipRepository.findPinnedByUserId(userId).ifPresent(existing -> {
            if (!existing.getCareGroupId().equals(targetGroupId)) {
                existing.unpin();
                membershipRepository.saveAndFlush(existing);
            }
        });
    }
}
