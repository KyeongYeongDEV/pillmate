package com.pillmate.caregroup.application;

import com.pillmate.caregroup.application.port.InviteCodeCachePort;
import com.pillmate.caregroup.domain.event.MemberJoined;
import com.pillmate.caregroup.domain.model.MemberRole;
import com.pillmate.caregroup.domain.model.Membership;
import com.pillmate.caregroup.domain.repository.MembershipRepository;
import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.notification.application.port.RecipientCachePort;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class JoinGroupUseCase {

    private final InviteCodeCachePort inviteCodeCachePort;
    private final MembershipRepository membershipRepository;
    private final RecipientCachePort recipientCachePort;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Long join(String code, Long userId, MemberRole role) {
        requireJoinableRole(role);
        Long groupId = lookupGroupOrThrowExpired(code);
        requireNotAlreadyMember(groupId, userId);

        membershipRepository.save(Membership.of(groupId, userId, role, null));
        recipientCachePort.evict(groupId);
        // 알림 발송은 커밋 후(AFTER_COMMIT)에만 실행됨 — NotificationDispatcher.on(MemberJoined) 참고.
        // 발송 실패가 가입 자체에 영향 주지 않음(best-effort, 트랜잭션 내 외부호출 없음).
        eventPublisher.publishEvent(new MemberJoined(groupId, userId));
        return groupId;
    }

    private void requireJoinableRole(MemberRole role) {
        if (role == MemberRole.ADMIN) {
            throw new PillmateException(ErrorCode.INVALID_REQUEST);
        }
    }

    private Long lookupGroupOrThrowExpired(String code) {
        return inviteCodeCachePort.findGroupId(code)
                .orElseThrow(() -> new PillmateException(ErrorCode.INVITE_CODE_EXPIRED_OR_INVALID));
    }

    private void requireNotAlreadyMember(Long careGroupId, Long userId) {
        if (membershipRepository.existsByCareGroupIdAndUserId(careGroupId, userId)) {
            throw new PillmateException(ErrorCode.GROUP_ALREADY_MEMBER);
        }
    }
}
