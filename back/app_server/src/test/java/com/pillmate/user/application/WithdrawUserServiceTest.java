package com.pillmate.user.application;

import com.pillmate.caregroup.application.LeaveGroupUseCase;
import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.user.domain.model.User;
import com.pillmate.user.domain.model.UserProvider;
import com.pillmate.user.domain.repository.UserRepository;
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

@DisplayName("WithdrawUserService — 회원탈퇴 soft delete")
@ExtendWith(MockitoExtension.class)
class WithdrawUserServiceTest {

    private static final Long USER_ID = 7L;
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-07-04T00:00:00Z"), ZoneOffset.UTC);

    @Mock UserRepository userRepository;
    @Mock LeaveGroupUseCase leaveGroupUseCase;

    private WithdrawUserService sut() {
        return new WithdrawUserService(userRepository, leaveGroupUseCase, FIXED_CLOCK);
    }

    @Test
    @DisplayName("탈퇴 시 그룹 전체 탈퇴 + 계정 익명화 + 저장 (하드 DELETE 없음)")
    void withdraw_leavesAllGroupsAndAnonymizes() {
        User user = User.ofOAuth("kakao-1", UserProvider.KAKAO, "홍길동", "hong@example.com");
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

        sut().withdraw(USER_ID);

        verify(leaveGroupUseCase).leaveAll(USER_ID);
        verify(userRepository).save(user);
        assertThat(user.isWithdrawn()).isTrue();
        assertThat(user.getWithdrawnAt()).isEqualTo(Instant.parse("2026-07-04T00:00:00Z"));
        assertThat(user.getName()).isEqualTo("탈퇴한 사용자");
        assertThat(user.getEmail()).isNull();
    }

    @Test
    @DisplayName("존재하지 않는 사용자 → INVALID_AUTH_TOKEN, 그룹 탈퇴 미호출")
    void withdraw_userNotFound_throws() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> sut().withdraw(USER_ID))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_AUTH_TOKEN);

        verify(leaveGroupUseCase, never()).leaveAll(any());
        verify(userRepository, never()).save(any());
    }
}
