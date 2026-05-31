package com.pillmate.caregroup.application;

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

@DisplayName("PinGroupUseCase — 단위 테스트")
@ExtendWith(MockitoExtension.class)
class PinGroupUseCaseTest {

    @Mock MembershipRepository membershipRepository;
    @InjectMocks PinGroupUseCase sut;

    private static final Long USER_ID = 1L;
    private static final Long GROUP_A = 10L;
    private static final Long GROUP_B = 20L;

    @Test
    @DisplayName("기존 핀 없으면 신규 그룹만 핀")
    void pin_noExistingPin_pinsTarget() {
        Membership target = Membership.of(GROUP_A, USER_ID, MemberRole.PATIENT, null);
        given(membershipRepository.findByCareGroupIdAndUserId(GROUP_A, USER_ID)).willReturn(Optional.of(target));
        given(membershipRepository.findPinnedByUserId(USER_ID)).willReturn(Optional.empty());

        sut.pin(GROUP_A, USER_ID);

        assertThat(target.isPinned()).isTrue();
        verify(membershipRepository).save(target);
    }

    @Test
    @DisplayName("기존 핀 그룹과 신규 그룹 다르면 기존 핀 해제 + 신규 핀")
    void pin_existingDifferent_unpinThenPin() {
        Membership existing = Membership.of(GROUP_B, USER_ID, MemberRole.PATIENT, null);
        existing.pin();
        Membership target = Membership.of(GROUP_A, USER_ID, MemberRole.PATIENT, null);
        given(membershipRepository.findByCareGroupIdAndUserId(GROUP_A, USER_ID)).willReturn(Optional.of(target));
        given(membershipRepository.findPinnedByUserId(USER_ID)).willReturn(Optional.of(existing));

        sut.pin(GROUP_A, USER_ID);

        assertThat(existing.isPinned()).isFalse();
        assertThat(target.isPinned()).isTrue();
        verify(membershipRepository).save(existing);
        verify(membershipRepository).save(target);
    }

    @Test
    @DisplayName("이미 같은 그룹 핀이면 noop")
    void pin_alreadyPinned_isIdempotent() {
        Membership target = Membership.of(GROUP_A, USER_ID, MemberRole.PATIENT, null);
        target.pin();
        given(membershipRepository.findByCareGroupIdAndUserId(GROUP_A, USER_ID)).willReturn(Optional.of(target));
        given(membershipRepository.findPinnedByUserId(USER_ID)).willReturn(Optional.of(target));

        sut.pin(GROUP_A, USER_ID);

        assertThat(target.isPinned()).isTrue();
    }

    @Test
    @DisplayName("멤버십 없으면 GROUP_ACCESS_DENIED 예외")
    void pin_nonMember_throws() {
        given(membershipRepository.findByCareGroupIdAndUserId(GROUP_A, USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> sut.pin(GROUP_A, USER_ID))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GROUP_ACCESS_DENIED);

        verify(membershipRepository, never()).save(any());
    }
}
