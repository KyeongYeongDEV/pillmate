package com.pillmate.caregroup.application;

import com.pillmate.caregroup.application.dto.InviteCodeResponse;
import com.pillmate.caregroup.application.port.InviteCodeCachePort;
import com.pillmate.caregroup.domain.model.InviteCode;
import com.pillmate.caregroup.domain.repository.InviteCodeRepository;
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

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@DisplayName("IssueInviteCodeUseCase — 멤버만 코드 발급")
@ExtendWith(MockitoExtension.class)
class IssueInviteCodeUseCaseTest {

    @Mock InviteCodeRepository inviteCodeRepository;
    @Mock MembershipRepository membershipRepository;
    @Mock InviteCodeCachePort inviteCodeCachePort;
    @InjectMocks IssueInviteCodeUseCase sut;

    @Test
    @DisplayName("호출자가 멤버가 아니면 GROUP_ACCESS_DENIED")
    void issue_whenNotMember_throwsAccessDenied() {
        given(membershipRepository.existsByCareGroupIdAndUserId(1L, 99L)).willReturn(false);

        assertThatThrownBy(() -> sut.issue(1L, 99L))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GROUP_ACCESS_DENIED);
    }

    @Test
    @DisplayName("멤버가 발급하면 6자리 코드 + expiresAt 반환")
    void issue_returnsCode() {
        given(membershipRepository.existsByCareGroupIdAndUserId(1L, 1L)).willReturn(true);
        given(inviteCodeRepository.save(any(InviteCode.class)))
                .willAnswer(inv -> inv.getArgument(0));

        InviteCodeResponse response = sut.issue(1L, 1L);

        assertThat(response.code()).hasSize(6).matches("[A-Z0-9]{6}");
        assertThat(response.expiresAt()).isNotNull();
    }

    @Test
    @DisplayName("발급 성공 시 Redis 에도 코드→groupId 매핑 저장 (TTL 1분 — FE 카운트다운 UX)")
    void issue_alsoStoresToRedisCache() {
        given(membershipRepository.existsByCareGroupIdAndUserId(7L, 1L)).willReturn(true);
        given(inviteCodeRepository.save(any(InviteCode.class)))
                .willAnswer(inv -> inv.getArgument(0));

        InviteCodeResponse response = sut.issue(7L, 1L);

        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(inviteCodeCachePort).put(eq(response.code()), eq(7L), ttl.capture());
        assertThat(ttl.getValue()).isEqualTo(Duration.ofMinutes(1));
    }
}
