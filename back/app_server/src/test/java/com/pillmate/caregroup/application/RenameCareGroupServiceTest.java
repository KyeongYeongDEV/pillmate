package com.pillmate.caregroup.application;

import com.pillmate.caregroup.domain.model.CareGroup;
import com.pillmate.caregroup.domain.repository.CareGroupRepository;
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

@DisplayName("RenameCareGroupService — 단위 테스트")
@ExtendWith(MockitoExtension.class)
class RenameCareGroupServiceTest {

    @Mock CareGroupRepository careGroupRepository;
    @Mock MembershipRepository membershipRepository;
    @InjectMocks RenameCareGroupService sut;

    private static final Long GROUP_ID = 10L;
    private static final Long USER_ID = 1L;

    @Test
    @DisplayName("구성원이면 이름이 바뀌고 save 호출됨")
    void rename_member_savesRenamedGroup() {
        CareGroup group = CareGroup.create("우리 가족", USER_ID);
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, USER_ID)).willReturn(true);
        given(careGroupRepository.findById(GROUP_ID)).willReturn(Optional.of(group));

        sut.rename(GROUP_ID, USER_ID, "새 가족 이름");

        assertThat(group.getName()).isEqualTo("새 가족 이름");
        verify(careGroupRepository).save(group);
    }

    @Test
    @DisplayName("비구성원이면 GROUP_ACCESS_DENIED, 조회/저장 미실행")
    void rename_nonMember_throwsAndSkipsLookup() {
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, USER_ID)).willReturn(false);

        assertThatThrownBy(() -> sut.rename(GROUP_ID, USER_ID, "새 가족 이름"))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GROUP_ACCESS_DENIED);

        verify(careGroupRepository, never()).findById(any());
        verify(careGroupRepository, never()).save(any());
    }

    @Test
    @DisplayName("존재하지 않는 그룹이면 GROUP_NOT_FOUND")
    void rename_groupNotFound_throws() {
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, USER_ID)).willReturn(true);
        given(careGroupRepository.findById(GROUP_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> sut.rename(GROUP_ID, USER_ID, "새 가족 이름"))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GROUP_NOT_FOUND);

        verify(careGroupRepository, never()).save(any());
    }
}
