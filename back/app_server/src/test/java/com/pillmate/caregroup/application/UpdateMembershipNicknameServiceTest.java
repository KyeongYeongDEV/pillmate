package com.pillmate.caregroup.application;

import com.pillmate.caregroup.domain.model.MemberRole;
import com.pillmate.caregroup.domain.model.Membership;
import com.pillmate.caregroup.domain.repository.MembershipRepository;
import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("UpdateMembershipNicknameService — 단위 테스트")
@ExtendWith(MockitoExtension.class)
class UpdateMembershipNicknameServiceTest {

    @Mock MembershipRepository membershipRepository;
    @InjectMocks UpdateMembershipNicknameService sut;

    private static final Long GROUP_ID = 10L;
    private static final Long USER_ID = 1L;

    @Test
    @DisplayName("구성원 본인 — 별명이 바뀌고 save 호출됨")
    void updateNickname_member_savesUpdatedMembership() {
        Membership membership = Membership.of(GROUP_ID, USER_ID, MemberRole.PATIENT, null);
        given(membershipRepository.findByCareGroupIdAndUserId(GROUP_ID, USER_ID))
                .willReturn(Optional.of(membership));

        sut.updateNickname(GROUP_ID, USER_ID, "삼촌");

        ArgumentCaptor<Membership> captor = ArgumentCaptor.forClass(Membership.class);
        verify(membershipRepository).save(captor.capture());
        assertThat(captor.getValue().getNickname()).isEqualTo("삼촌");
    }

    @Test
    @DisplayName("공백/null 이면 별명 리셋(기본값=원래 이름) 후 save")
    void updateNickname_blank_resetsToNull() {
        Membership membership = Membership.of(GROUP_ID, USER_ID, MemberRole.PATIENT, null);
        membership.updateNickname("삼촌");
        given(membershipRepository.findByCareGroupIdAndUserId(GROUP_ID, USER_ID))
                .willReturn(Optional.of(membership));

        sut.updateNickname(GROUP_ID, USER_ID, "   ");

        ArgumentCaptor<Membership> captor = ArgumentCaptor.forClass(Membership.class);
        verify(membershipRepository).save(captor.capture());
        assertThat(captor.getValue().getNickname()).isNull();
    }

    @Test
    @DisplayName("비구성원(해당 그룹 멤버십 없음) — GROUP_ACCESS_DENIED, save 미실행")
    void updateNickname_notMember_throwsAndSkipsSave() {
        given(membershipRepository.findByCareGroupIdAndUserId(GROUP_ID, USER_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> sut.updateNickname(GROUP_ID, USER_ID, "삼촌"))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GROUP_ACCESS_DENIED);

        verify(membershipRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("20자 초과 — INVALID_REQUEST (도메인 IllegalArgumentException → 매핑)")
    void updateNickname_tooLong_throwsInvalidRequest() {
        Membership membership = Membership.of(GROUP_ID, USER_ID, MemberRole.PATIENT, null);
        given(membershipRepository.findByCareGroupIdAndUserId(GROUP_ID, USER_ID))
                .willReturn(Optional.of(membership));

        assertThatThrownBy(() -> sut.updateNickname(GROUP_ID, USER_ID, "가".repeat(21)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(membershipRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
