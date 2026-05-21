package com.pillmate.caregroup.application;

import com.pillmate.caregroup.application.dto.CreateGroupResponse;
import com.pillmate.caregroup.domain.model.CareGroup;
import com.pillmate.caregroup.domain.model.InviteCode;
import com.pillmate.caregroup.domain.repository.CareGroupRepository;
import com.pillmate.caregroup.domain.repository.InviteCodeRepository;
import com.pillmate.caregroup.domain.repository.MembershipRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@DisplayName("CreateCareGroupUseCase")
@ExtendWith(MockitoExtension.class)
class CreateCareGroupUseCaseTest {

    @Mock CareGroupRepository careGroupRepository;
    @Mock MembershipRepository membershipRepository;
    @Mock InviteCodeRepository inviteCodeRepository;
    @InjectMocks CreateCareGroupUseCase sut;

    @Test
    @DisplayName("그룹 생성 시 그룹, 멤버십, 초대코드가 모두 저장된다")
    void create_savesGroupAndMembershipAndInviteCode() {
        CareGroup saved = CareGroup.create("우리 가족", 1L);
        InviteCode savedCode = InviteCode.generate(null, 1L);
        given(careGroupRepository.save(any())).willReturn(saved);
        given(inviteCodeRepository.save(any())).willReturn(savedCode);

        CreateGroupResponse response = sut.create("우리 가족", 1L);

        verify(careGroupRepository).save(any());
        verify(membershipRepository).save(any());
        verify(inviteCodeRepository).save(any());
        assertThat(response.name()).isEqualTo("우리 가족");
        assertThat(response.inviteCode()).hasSize(6);
    }
}
