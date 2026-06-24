package com.pillmate.user.application;

import com.pillmate.common.security.JwtTokenProvider;
import com.pillmate.user.application.dto.AuthResult;
import com.pillmate.user.application.port.KakaoOAuthPort;
import com.pillmate.user.application.port.KakaoProfile;
import com.pillmate.user.domain.model.User;
import com.pillmate.user.domain.model.UserProvider;
import com.pillmate.user.domain.repository.UserRepository;
import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("KakaoLoginService — 신규가입/기존로그인/dev fallback")
@ExtendWith(MockitoExtension.class)
class KakaoLoginServiceTest {

    @Mock UserRepository userRepository;
    @Mock KakaoOAuthPort kakaoOAuthPort;
    @Mock JwtTokenProvider jwtTokenProvider;
    @InjectMocks KakaoLoginService sut;

    private static final KakaoProfile KAKAO_PROFILE =
            new KakaoProfile("kakao-id-1", "홍길동", "hong@test.com", "https://profile.img");

    @BeforeEach
    void setUp() {
        lenient().when(jwtTokenProvider.issue(any())).thenReturn("test.jwt.token");
    }

    @Test
    @DisplayName("카카오 신규 가입 — isNewUser=true, 유저 저장")
    void login_newUser_savesAndReturnsNewUser() {
        given(kakaoOAuthPort.isConfigured()).willReturn(true);
        given(kakaoOAuthPort.exchange("code", "redirect")).willReturn(KAKAO_PROFILE);
        given(userRepository.findByProviderAndExternalId(UserProvider.KAKAO, "kakao-id-1"))
                .willReturn(Optional.empty());
        User saved = userWithId(10L, "홍길동");
        given(userRepository.save(any(User.class))).willReturn(saved);

        AuthResult result = sut.login("code", "redirect");

        assertThat(result.isNewUser()).isTrue();
        assertThat(result.userId()).isEqualTo(10L);
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("기존 카카오 사용자 — isNewUser=false, 저장 없음")
    void login_existingUser_noSave() {
        given(kakaoOAuthPort.isConfigured()).willReturn(true);
        given(kakaoOAuthPort.exchange("code", "redirect")).willReturn(KAKAO_PROFILE);
        User existing = userWithId(5L, "홍길동");
        given(userRepository.findByProviderAndExternalId(UserProvider.KAKAO, "kakao-id-1"))
                .willReturn(Optional.of(existing));

        AuthResult result = sut.login("code", "redirect");

        assertThat(result.isNewUser()).isFalse();
        assertThat(result.userId()).isEqualTo(5L);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("dev fallback(미구성) — 플래그 true 시 seed userId=1 반환, 카카오 호출 없음")
    void login_devFallback_returnsSeedUser() {
        ReflectionTestUtils.setField(sut, "devFallbackEnabled", true);
        given(kakaoOAuthPort.isConfigured()).willReturn(false);
        User seed = userWithId(1L, "seed");
        given(userRepository.findById(1L)).willReturn(Optional.of(seed));

        AuthResult result = sut.login("", "");

        assertThat(result.userId()).isEqualTo(1L);
        assertThat(result.isNewUser()).isFalse();
        verify(kakaoOAuthPort, never()).exchange(any(), any());
    }

    @Test
    @DisplayName("dev fallback(code 빈값) — 플래그 true 시 seed userId=1 반환")
    void login_devFallback_emptyCode_returnsSeedUser() {
        ReflectionTestUtils.setField(sut, "devFallbackEnabled", true);
        given(kakaoOAuthPort.isConfigured()).willReturn(true);
        User seed = userWithId(1L, "seed");
        given(userRepository.findById(1L)).willReturn(Optional.of(seed));

        AuthResult result = sut.login("", "redirect");

        assertThat(result.userId()).isEqualTo(1L);
        verify(kakaoOAuthPort, never()).exchange(any(), any());
    }

    @Test
    @DisplayName("dev fallback 비활성(플래그 false) — 빈 code → KAKAO_AUTH_FAILED(401)")
    void login_devFallback_disabled_throwsKakaoAuthFailed() {
        // @InjectMocks sets boolean to false by default — no ReflectionTestUtils needed
        given(kakaoOAuthPort.isConfigured()).willReturn(false);

        assertThatThrownBy(() -> sut.login("", ""))
                .isInstanceOf(PillmateException.class)
                .satisfies(ex -> assertThat(((PillmateException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.KAKAO_AUTH_FAILED));

        verify(kakaoOAuthPort, never()).exchange(any(), any());
        verify(userRepository, never()).findById(any());
    }

    private User userWithId(Long id, String name) {
        User user = User.dummy(name);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
