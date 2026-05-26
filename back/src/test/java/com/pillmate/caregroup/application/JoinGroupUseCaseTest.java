package com.pillmate.caregroup.application;

import com.pillmate.caregroup.domain.model.InviteCode;
import com.pillmate.caregroup.domain.model.MemberRole;
import com.pillmate.caregroup.domain.model.Membership;
import com.pillmate.caregroup.domain.repository.InviteCodeRepository;
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
import static org.mockito.Mockito.verify;

@DisplayName("JoinGroupUseCase — 초대코드 검증/사용/멤버 추가")
@ExtendWith(MockitoExtension.class)
class JoinGroupUseCaseTest {

    @Mock InviteCodeRepository inviteCodeRepository;
    @Mock MembershipRepository membershipRepository;
    @InjectMocks JoinGroupUseCase sut;

    @Test
    @DisplayName("정상 흐름: 코드 사용 + Membership 저장 + groupId 반환")
    void join_whenSuccess_createsMembership() {
        InviteCode code = InviteCode.generate(1L, 10L);
        given(inviteCodeRepository.findByCode("ABC123")).willReturn(Optional.of(code));
        given(membershipRepository.existsByCareGroupIdAndUserId(1L, 20L)).willReturn(false);

        Long groupId = sut.join("ABC123", 20L, MemberRole.PATIENT);

        assertThat(groupId).isEqualTo(1L);
        assertThat(code.getUsedAt()).isNotNull();
        verify(membershipRepository).save(any(Membership.class));
    }

    @Test
    @DisplayName("만료된 코드면 GROUP_INVITE_CODE_EXPIRED")
    void join_whenCodeExpired_throws() {
        InviteCode expired = InviteCode.ofExpired("ABC123", 1L, 10L);
        given(inviteCodeRepository.findByCode("ABC123")).willReturn(Optional.of(expired));

        assertThatThrownBy(() -> sut.join("ABC123", 20L, MemberRole.PATIENT))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GROUP_INVITE_CODE_EXPIRED);
    }

    @Test
    @DisplayName("이미 사용된 코드면 GROUP_INVITE_CODE_USED")
    void join_whenCodeAlreadyUsed_throws() {
        InviteCode used = InviteCode.generate(1L, 10L);
        used.markUsed();
        given(inviteCodeRepository.findByCode("ABC123")).willReturn(Optional.of(used));

        assertThatThrownBy(() -> sut.join("ABC123", 20L, MemberRole.PATIENT))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GROUP_INVITE_CODE_USED);
    }

    @Test
    @DisplayName("이미 멤버이면 GROUP_ALREADY_MEMBER")
    void join_whenAlreadyMember_throws() {
        InviteCode code = InviteCode.generate(1L, 10L);
        given(inviteCodeRepository.findByCode("ABC123")).willReturn(Optional.of(code));
        given(membershipRepository.existsByCareGroupIdAndUserId(1L, 20L)).willReturn(true);

        assertThatThrownBy(() -> sut.join("ABC123", 20L, MemberRole.PATIENT))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GROUP_ALREADY_MEMBER);
    }

    @Test
    @DisplayName("코드 자체가 없으면 GROUP_INVITE_CODE_INVALID")
    void join_whenCodeMissing_throws() {
        given(inviteCodeRepository.findByCode("ZZZZZZ")).willReturn(Optional.empty());

        assertThatThrownBy(() -> sut.join("ZZZZZZ", 20L, MemberRole.PATIENT))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GROUP_INVITE_CODE_INVALID);
    }

    @Test
    @DisplayName("ADMIN 역할로 join 시도 시 INVALID_REQUEST (생성자만 ADMIN)")
    void join_whenRoleAdmin_throws() {
        assertThatThrownBy(() -> sut.join("ABC123", 20L, MemberRole.ADMIN))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REQUEST);
    }
}
