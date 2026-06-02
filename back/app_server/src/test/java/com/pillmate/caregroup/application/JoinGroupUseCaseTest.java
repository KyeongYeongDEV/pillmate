package com.pillmate.caregroup.application;

import com.pillmate.caregroup.application.port.InviteCodeCachePort;
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
}
