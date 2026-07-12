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
    @Mock com.pillmate.notification.application.port.RecipientCachePort recipientCachePort;

    private static final Long USER_ID = 1L;
    private static final Long GROUP_ID = 10L;
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-14T09:00:00Z"), ZoneOffset.UTC);

    private LeaveGroupUseCase sut() {
        return new LeaveGroupUseCase(membershipRepository, recipientCachePort, FIXED_CLOCK);
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

    // T-BE-REDIS-RECIPIENT-CACHE — 탈퇴 시 수신자 캐시 무효화
    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("탈퇴 성공 시 group recipients 캐시 evict")
    void leave_evictsRecipientCache() {
        Membership membership = Membership.of(GROUP_ID, USER_ID, MemberRole.PATIENT, null);
        given(membershipRepository.findByCareGroupIdAndUserId(GROUP_ID, USER_ID))
                .willReturn(java.util.Optional.of(membership));

        sut().leave(GROUP_ID, USER_ID);

        org.mockito.Mockito.verify(recipientCachePort).evict(GROUP_ID);
    }

    // T-BE-WITHDRAW — 회원탈퇴 시 본인 모든 활성 그룹 soft 탈퇴
    @Test
    @DisplayName("leaveAll — 활성 멤버십만 LEFT 전이 + 그룹별 캐시 evict, 이미 탈퇴한 건 건너뜀")
    void leaveAll_leavesOnlyActiveMemberships() {
        Membership active1 = Membership.of(10L, USER_ID, MemberRole.PATIENT, null);
        Membership active2 = Membership.of(20L, USER_ID, MemberRole.ADMIN, null);
        Membership alreadyLeft = Membership.of(30L, USER_ID, MemberRole.PATIENT, null);
        alreadyLeft.leave(FIXED_CLOCK);
        given(membershipRepository.findByUserId(USER_ID))
                .willReturn(java.util.List.of(active1, active2, alreadyLeft));

        sut().leaveAll(USER_ID);

        assertThat(active1.hasLeft()).isTrue();
        assertThat(active2.hasLeft()).isTrue();
        verify(membershipRepository).save(active1);
        verify(membershipRepository).save(active2);
        verify(membershipRepository, never()).save(alreadyLeft);
        verify(recipientCachePort).evict(10L);
        verify(recipientCachePort).evict(20L);
        verify(recipientCachePort, never()).evict(30L);
    }
}
