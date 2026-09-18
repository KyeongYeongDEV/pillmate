package com.pillmate.caregroup.application;

import com.pillmate.caregroup.domain.model.Membership;
import com.pillmate.caregroup.domain.repository.MembershipRepository;
import com.pillmate.user.domain.model.User;
import com.pillmate.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 그룹 문맥에서 사람 이름을 표시할 때 그룹별 별명(Membership.nickname) 을 실명보다 우선한다.
// 알림 발송처럼 그룹 소속(groupId)이 확정된 모든 경로에서 재사용해 표기 일관성을 보장한다.
@Component
@RequiredArgsConstructor
public class GroupDisplayNameResolver {

    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;

    public String resolve(Long groupId, Long userId) {
        if (userId == null) {
            return null;
        }
        String nickname = resolveNickname(groupId, userId);
        return nickname != null ? nickname : resolveRealName(userId);
    }

    private String resolveNickname(Long groupId, Long userId) {
        if (groupId == null) {
            return null;
        }
        return membershipRepository.findByCareGroupIdAndUserId(groupId, userId)
                .map(Membership::getNickname)
                .filter(n -> n != null && !n.isBlank())
                .orElse(null);
    }

    private String resolveRealName(Long userId) {
        return userRepository.findById(userId).map(User::getName).orElse(null);
    }
}
