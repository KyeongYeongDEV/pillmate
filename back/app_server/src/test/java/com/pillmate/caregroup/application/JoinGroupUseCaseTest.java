package com.pillmate.caregroup.application;

import com.pillmate.caregroup.application.port.InviteCodeCachePort;
import com.pillmate.caregroup.domain.event.MemberJoined;
import com.pillmate.caregroup.domain.model.MemberRole;
import com.pillmate.caregroup.domain.model.Membership;
import com.pillmate.caregroup.domain.repository.MembershipRepository;
import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("JoinGroupUseCase — Redis lookup 우선 / Postgres path 제거")
@ExtendWith(MockitoExtension.class)
class JoinGroupUseCaseTest {

    @Mock InviteCodeCachePort inviteCodeCachePort;
    @Mock MembershipRepository membershipRepository;
    @Mock com.pillmate.notification.application.port.RecipientCachePort recipientCachePort;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks JoinGroupUseCase sut;

    private static final String CODE = "ABC123";
    private static final Long GROUP_ID = 1L;
    private static final Long USER_ID = 20L;

    @Test
    @DisplayName("정상 흐름: Redis hit → Membership 저장 + groupId 반환")
    void join_whenRedisHit_createsMembership() {
        given(inviteCodeCachePort.findGroupId(CODE)).willReturn(Optional.of(GROUP_ID));
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, USER_ID)).willReturn(false);

        Long groupId = sut.join(CODE, USER_ID, MemberRole.PATIENT);

        assertThat(groupId).isEqualTo(GROUP_ID);
        verify(membershipRepository).save(any(Membership.class));
    }

    @Test
    @DisplayName("Redis miss 면 PILL_096 INVITE_CODE_EXPIRED_OR_INVALID")
    void join_whenRedisMiss_throwsExpiredOrInvalid() {
        given(inviteCodeCachePort.findGroupId(CODE)).willReturn(Optional.empty());

        assertThatThrownBy(() -> sut.join(CODE, USER_ID, MemberRole.PATIENT))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVITE_CODE_EXPIRED_OR_INVALID);

        verify(membershipRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 멤버이면 GROUP_ALREADY_MEMBER")
    void join_whenAlreadyMember_throws() {
        given(inviteCodeCachePort.findGroupId(CODE)).willReturn(Optional.of(GROUP_ID));
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, USER_ID)).willReturn(true);

        assertThatThrownBy(() -> sut.join(CODE, USER_ID, MemberRole.PATIENT))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GROUP_ALREADY_MEMBER);
    }

    @Test
    @DisplayName("ADMIN 역할 join 시도 시 INVALID_REQUEST")
    void join_whenRoleAdmin_throws() {
        assertThatThrownBy(() -> sut.join(CODE, USER_ID, MemberRole.ADMIN))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REQUEST);
    }

    // T-BE-REDIS-RECIPIENT-CACHE — 가입 시 수신자 캐시 무효화
    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("가입 성공 시 group recipients 캐시 evict")
    void join_evictsRecipientCache() {
        org.mockito.BDDMockito.given(inviteCodeCachePort.findGroupId(CODE))
                .willReturn(java.util.Optional.of(GROUP_ID));
        org.mockito.BDDMockito.given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, USER_ID))
                .willReturn(false);

        sut.join(CODE, USER_ID, com.pillmate.caregroup.domain.model.MemberRole.GUARDIAN);

        org.mockito.Mockito.verify(recipientCachePort).evict(GROUP_ID);
    }

    // T-GROUP-JOIN-REALTIME-PUSH — 가입 성공 시 MemberJoined 이벤트 발행(알림은 AFTER_COMMIT 에서 처리, 여기선 발행만 검증)
    @Test
    @DisplayName("가입 성공 시 MemberJoined(groupId, userId) 이벤트 발행")
    void join_publishesMemberJoinedEvent() {
        given(inviteCodeCachePort.findGroupId(CODE)).willReturn(Optional.of(GROUP_ID));
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, USER_ID)).willReturn(false);

        sut.join(CODE, USER_ID, MemberRole.PATIENT);

        verify(eventPublisher).publishEvent(new MemberJoined(GROUP_ID, USER_ID));
    }

    @Test
    @DisplayName("이미 멤버/ADMIN role 등 실패 시 MemberJoined 이벤트 발행 안 함")
    void join_whenFails_doesNotPublishEvent() {
        given(inviteCodeCachePort.findGroupId(CODE)).willReturn(Optional.of(GROUP_ID));
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, USER_ID)).willReturn(true);

        assertThatThrownBy(() -> sut.join(CODE, USER_ID, MemberRole.PATIENT))
                .isInstanceOf(PillmateException.class);

        verify(eventPublisher, never()).publishEvent(any());
    }
}
