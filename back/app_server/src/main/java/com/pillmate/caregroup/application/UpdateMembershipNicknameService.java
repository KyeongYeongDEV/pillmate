package com.pillmate.caregroup.application;

import com.pillmate.caregroup.domain.model.Membership;
import com.pillmate.caregroup.domain.repository.MembershipRepository;
import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 본인 그룹별 별명 변경 — 다른 구성원 별명은 수정 불가(그룹ID+본인userId 로만 조회하므로 본인 것만 대상이 됨).
@Service
@RequiredArgsConstructor
public class UpdateMembershipNicknameService {

    private final MembershipRepository membershipRepository;

    @Transactional
    public void updateNickname(Long groupId, Long userId, String nickname) {
        Membership membership = findOwnMembership(groupId, userId);
        membership.updateNickname(nickname);
        membershipRepository.save(membership);
    }

    private Membership findOwnMembership(Long groupId, Long userId) {
        return membershipRepository.findByCareGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new PillmateException(ErrorCode.GROUP_ACCESS_DENIED));
    }
}
