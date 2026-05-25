package com.pillmate.common.security;

import com.pillmate.caregroup.domain.model.Membership;
import com.pillmate.caregroup.domain.repository.MembershipRepository;
import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component("careGroupGuard")
@RequiredArgsConstructor
public class CareGroupGuard {

    private final MembershipRepository membershipRepository;

    public boolean canAccess(Long groupId) {
        Long userId = UserContext.get();
        if (userId == null || groupId == null) {
            return false;
        }
        return membershipRepository.existsByCareGroupIdAndUserId(groupId, userId);
    }

    public boolean isAdmin(Long groupId) {
        Long userId = UserContext.get();
        if (userId == null || groupId == null) {
            return false;
        }
        return membershipRepository.findByCareGroupIdAndUserId(groupId, userId)
                .map(Membership::isAdmin)
                .orElse(false);
    }

    public void requireAccessible(Long groupId) {
        if (!canAccess(groupId)) {
            throw new PillmateException(ErrorCode.GROUP_ACCESS_DENIED);
        }
    }

    public void requirePatientAccessible(Long patientId) {
        Long userId = UserContext.get();
        if (userId == null || patientId == null) {
            throw new PillmateException(ErrorCode.GROUP_ACCESS_DENIED);
        }
        if (userId.equals(patientId)) {
            return;
        }
        if (!membershipRepository.existsSharedGroup(userId, patientId)) {
            throw new PillmateException(ErrorCode.GROUP_ACCESS_DENIED);
        }
    }
}
