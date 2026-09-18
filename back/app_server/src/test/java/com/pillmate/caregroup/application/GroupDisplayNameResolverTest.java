package com.pillmate.caregroup.application;

import com.pillmate.caregroup.domain.model.MemberRole;
import com.pillmate.caregroup.domain.model.Membership;
import com.pillmate.caregroup.domain.repository.MembershipRepository;
import com.pillmate.user.domain.model.User;
import com.pillmate.user.domain.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 알림 본문의 이름 표기가 그룹별 별명(Membership.nickname) 을 우선하도록 통일 —
// 기존엔 알림 발송 경로(SendGroupDoseNotificationService 등)가 실명만 조회해
// 별명을 바꿔도 알림 내용은 그대로 실명으로 나가던 문제를 해결한다(사용자 요청 2026-09-18).
@DisplayName("GroupDisplayNameResolver — 단위 테스트")
@ExtendWith(MockitoExtension.class)
class GroupDisplayNameResolverTest {

    @Mock MembershipRepository membershipRepository;
    @Mock UserRepository userRepository;
    @InjectMocks GroupDisplayNameResolver sut;

    private static final Long GROUP_ID = 10L;
    private static final Long USER_ID = 1L;

    @Test
    @DisplayName("그룹별 별명이 설정돼 있으면 별명을 반환한다")
    void resolve_withNickname_returnsNickname() {
        Membership membership = Membership.of(GROUP_ID, USER_ID, MemberRole.PATIENT, null);
        membership.updateNickname("삼촌");
        given(membershipRepository.findByCareGroupIdAndUserId(GROUP_ID, USER_ID))
                .willReturn(Optional.of(membership));

        String name = sut.resolve(GROUP_ID, USER_ID);

        assertThat(name).isEqualTo("삼촌");
        verify(userRepository, never()).findById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("별명 없으면 실명으로 fallback")
    void resolve_withoutNickname_fallsBackToRealName() {
        Membership membership = Membership.of(GROUP_ID, USER_ID, MemberRole.PATIENT, null);
        given(membershipRepository.findByCareGroupIdAndUserId(GROUP_ID, USER_ID))
                .willReturn(Optional.of(membership));
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(User.dummy("홍길동")));

        String name = sut.resolve(GROUP_ID, USER_ID);

        assertThat(name).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("groupId null(솔로/그룹무관) 이면 멤버십 조회 없이 실명 바로 반환")
    void resolve_groupIdNull_returnsRealNameWithoutMembershipLookup() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(User.dummy("홍길동")));

        String name = sut.resolve(null, USER_ID);

        assertThat(name).isEqualTo("홍길동");
        verify(membershipRepository, never()).findByCareGroupIdAndUserId(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("userId null 이면 null 반환 (조회 자체를 안 함)")
    void resolve_userIdNull_returnsNull() {
        String name = sut.resolve(GROUP_ID, null);

        assertThat(name).isNull();
        verify(membershipRepository, never()).findByCareGroupIdAndUserId(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(userRepository, never()).findById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("해당 그룹 멤버십이 없으면(탈퇴 등) 실명으로 fallback")
    void resolve_noMembership_fallsBackToRealName() {
        given(membershipRepository.findByCareGroupIdAndUserId(GROUP_ID, USER_ID))
                .willReturn(Optional.empty());
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(User.dummy("홍길동")));

        String name = sut.resolve(GROUP_ID, USER_ID);

        assertThat(name).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("유저 자체가 없으면 null 반환")
    void resolve_userNotFound_returnsNull() {
        given(membershipRepository.findByCareGroupIdAndUserId(GROUP_ID, USER_ID))
                .willReturn(Optional.empty());
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        String name = sut.resolve(GROUP_ID, USER_ID);

        assertThat(name).isNull();
    }
}
