package com.pillmate.user.application;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.user.application.dto.UserProfileResponse;
import com.pillmate.user.domain.model.User;
import com.pillmate.user.domain.model.UserProvider;
import com.pillmate.user.domain.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("UpdateProfileService — 닉네임 변경")
@ExtendWith(MockitoExtension.class)
class UpdateProfileServiceTest {

    private static final Long USER_ID = 7L;

    @Mock UserRepository userRepository;

    private UpdateProfileService sut() {
        return new UpdateProfileService(userRepository);
    }

    @Test
    @DisplayName("정상 변경 — 도메인 updateName 경유 + save 후 변경된 프로필 반환")
    void updateName_success_returnsUpdatedProfile() {
        User user = User.ofOAuth("kakao-1", UserProvider.KAKAO, "홍길동", "hong@example.com");
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(userRepository.save(user)).willReturn(user);

        UserProfileResponse response = sut().updateName(USER_ID, "새이름");

        assertThat(response.name()).isEqualTo("새이름");
        assertThat(response.email()).isEqualTo("hong@example.com");
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("존재하지 않는 사용자 → INVALID_AUTH_TOKEN, save 미호출")
    void updateName_userNotFound_throws() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> sut().updateName(USER_ID, "새이름"))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_AUTH_TOKEN);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("탈퇴한 계정 → ACCOUNT_WITHDRAWN, save 미호출 (익명화 되돌리기 방지)")
    void updateName_withdrawnUser_throwsAccountWithdrawn() {
        User withdrawn = User.ofOAuth("kakao-1", UserProvider.KAKAO, "홍길동", "hong@example.com");
        withdrawn.withdraw(Instant.parse("2026-07-01T00:00:00Z"));
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(withdrawn));

        assertThatThrownBy(() -> sut().updateName(USER_ID, "새이름"))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCOUNT_WITHDRAWN);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("blank 이름 → 도메인 IllegalArgumentException 전파, save 미호출")
    void updateName_blankName_propagatesDomainException() {
        User user = User.ofOAuth("kakao-1", UserProvider.KAKAO, "홍길동", "hong@example.com");
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> sut().updateName(USER_ID, "  "))
                .isInstanceOf(IllegalArgumentException.class);

        verify(userRepository, never()).save(any());
    }
}
