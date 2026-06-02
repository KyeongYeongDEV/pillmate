package com.pillmate.caregroup.application;

import com.pillmate.caregroup.application.dto.CreateGroupResponse;
import com.pillmate.caregroup.application.port.InviteCodeCachePort;
import com.pillmate.caregroup.domain.model.CareGroup;
import com.pillmate.caregroup.domain.model.InviteCode;
import com.pillmate.caregroup.domain.repository.CareGroupRepository;
import com.pillmate.caregroup.domain.repository.InviteCodeRepository;
import com.pillmate.caregroup.domain.repository.MembershipRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@DisplayName("CreateCareGroupUseCase")
@ExtendWith(MockitoExtension.class)
class CreateCareGroupUseCaseTest {

    @Mock CareGroupRepository careGroupRepository;
    @Mock MembershipRepository membershipRepository;
    @Mock InviteCodeRepository inviteCodeRepository;
    @Mock InviteCodeCachePort inviteCodeCachePort;
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

    @Test
    @DisplayName("그룹 생성 시 Redis SETEX 도 호출 (직후 가입 시 Redis hit 보장, TTL 1분)")
    void create_alsoPutsInviteCodeToRedis() {
        CareGroup saved = CareGroup.create("redis-smoke", 1L);
        InviteCode savedCode = InviteCode.generate(saved.getId(), 1L);
        given(careGroupRepository.save(any())).willReturn(saved);
        given(inviteCodeRepository.save(any())).willReturn(savedCode);

        sut.create("redis-smoke", 1L);

        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(inviteCodeCachePort).put(eq(savedCode.getCode()), eq(saved.getId()), ttl.capture());
        assertThat(ttl.getValue()).isEqualTo(Duration.ofMinutes(1));
    }
}
