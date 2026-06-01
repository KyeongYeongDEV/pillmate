package com.pillmate.caregroup.application;

import com.pillmate.caregroup.domain.repository.MembershipRepository;
import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UnpinGroupUseCase {

    private final MembershipRepository membershipRepository;

    @Transactional
    public void unpin(Long groupId, Long userId) {
        requireMember(groupId, userId);
        membershipRepository.findByCareGroupIdAndUserId(groupId, userId).ifPresent(m -> {
            if (m.isPinned()) {
                m.unpin();
                membershipRepository.save(m);
            }
        });
    }

    private void requireMember(Long groupId, Long userId) {
        if (!membershipRepository.existsByCareGroupIdAndUserId(groupId, userId)) {
            throw new PillmateException(ErrorCode.GROUP_ACCESS_DENIED);
        }
    }
}
