package com.pillmate.caregroup.application;

import com.pillmate.caregroup.domain.model.MemberRole;
import com.pillmate.caregroup.domain.model.Membership;
import com.pillmate.caregroup.domain.repository.MembershipRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("UnpinGroupUseCase — 단위 테스트")
@ExtendWith(MockitoExtension.class)
class UnpinGroupUseCaseTest {

    @Mock MembershipRepository membershipRepository;
    @InjectMocks UnpinGroupUseCase sut;

    private static final Long USER_ID = 1L;
    private static final Long GROUP_ID = 10L;

    @Test
    @DisplayName("핀된 그룹 unpin")
    void unpin_pinnedGroup_unpins() {
        Membership membership = Membership.of(GROUP_ID, USER_ID, MemberRole.PATIENT, null);
        membership.pin();
        given(membershipRepository.findByCareGroupIdAndUserId(GROUP_ID, USER_ID)).willReturn(Optional.of(membership));

        sut.unpin(GROUP_ID, USER_ID);

        assertThat(membership.isPinned()).isFalse();
        verify(membershipRepository).save(membership);
    }

    @Test
    @DisplayName("멤버십 없으면 noop (idempotent)")
    void unpin_nonMember_isNoop() {
        given(membershipRepository.findByCareGroupIdAndUserId(GROUP_ID, USER_ID)).willReturn(Optional.empty());

        sut.unpin(GROUP_ID, USER_ID);

        verify(membershipRepository, never()).save(any());
    }
}
