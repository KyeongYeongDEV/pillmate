package com.pillmate.caregroup.application;

import com.pillmate.caregroup.domain.model.MemberRole;
import com.pillmate.caregroup.domain.model.Membership;
import com.pillmate.caregroup.domain.repository.MembershipRepository;
import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("LeaveGroupUseCase — 단위 테스트")
@ExtendWith(MockitoExtension.class)
class LeaveGroupUseCaseTest {

    @Mock MembershipRepository membershipRepository;

    private static final Long USER_ID = 1L;
    private static final Long GROUP_ID = 10L;
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-14T09:00:00Z"), ZoneOffset.UTC);

    private LeaveGroupUseCase sut() {
        return new LeaveGroupUseCase(membershipRepository, FIXED_CLOCK);
    }

    @Test
    @DisplayName("ACTIVE 멤버 본인 탈퇴 시 LEFT 전이 + 저장")
    void leave_activeMember_marksLeft() {
        Membership membership = Membership.of(GROUP_ID, USER_ID, MemberRole.PATIENT, null);
        given(membershipRepository.findByCareGroupIdAndUserId(GROUP_ID, USER_ID))
                .willReturn(Optional.of(membership));

        sut().leave(GROUP_ID, USER_ID);

        assertThat(membership.hasLeft()).isTrue();
        verify(membershipRepository).save(membership);
    }

    @Test
    @DisplayName("비멤버(또는 이미 탈퇴) 탈퇴 시 GROUP_ACCESS_DENIED — ACTIVE 조회 비어있음")
    void leave_nonMember_throws() {
        given(membershipRepository.findByCareGroupIdAndUserId(GROUP_ID, USER_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> sut().leave(GROUP_ID, USER_ID))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GROUP_ACCESS_DENIED);

        verify(membershipRepository, never()).save(any());
    }

    @Test
    @DisplayName("ADMIN(그룹장) 탈퇴 허용 — MVP 위임 로직 없이 LEFT 전이")
    void leave_admin_allowed() {
        Membership admin = Membership.of(GROUP_ID, USER_ID, MemberRole.ADMIN, null);
        given(membershipRepository.findByCareGroupIdAndUserId(GROUP_ID, USER_ID))
                .willReturn(Optional.of(admin));

        sut().leave(GROUP_ID, USER_ID);

        assertThat(admin.hasLeft()).isTrue();
        verify(membershipRepository).save(admin);
    }
}
