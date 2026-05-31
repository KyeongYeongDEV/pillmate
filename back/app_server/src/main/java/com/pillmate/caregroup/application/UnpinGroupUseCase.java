package com.pillmate.caregroup.application;

import com.pillmate.caregroup.domain.repository.MembershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UnpinGroupUseCase {

    private final MembershipRepository membershipRepository;

    @Transactional
    public void unpin(Long groupId, Long userId) {
        membershipRepository.findByCareGroupIdAndUserId(groupId, userId).ifPresent(m -> {
            if (m.isPinned()) {
                m.unpin();
                membershipRepository.save(m);
            }
        });
    }
}
