package com.pillmate.caregroup.application;

import com.pillmate.caregroup.domain.model.CareGroup;
import com.pillmate.caregroup.domain.repository.CareGroupRepository;
import com.pillmate.caregroup.domain.repository.MembershipRepository;
import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RenameCareGroupService {

    private final CareGroupRepository careGroupRepository;
    private final MembershipRepository membershipRepository;

    @Transactional
    public void rename(Long groupId, Long userId, String newName) {
        requireActiveMember(groupId, userId);
        CareGroup group = findGroup(groupId);
        group.rename(newName);
        careGroupRepository.save(group);
    }

    private void requireActiveMember(Long groupId, Long userId) {
        if (!membershipRepository.existsByCareGroupIdAndUserId(groupId, userId)) {
            throw new PillmateException(ErrorCode.GROUP_ACCESS_DENIED);
        }
    }

    private CareGroup findGroup(Long groupId) {
        return careGroupRepository.findById(groupId)
                .orElseThrow(() -> new PillmateException(ErrorCode.GROUP_NOT_FOUND));
    }
}
