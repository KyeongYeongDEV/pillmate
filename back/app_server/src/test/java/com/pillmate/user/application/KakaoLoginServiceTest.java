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

    // T-DEV-USERID-SELECTOR: dev fallback 시 X-Dev-User-Id 헤더로 seed user 선택
    @Test
    @DisplayName("dev fallback + devUserId=2 (seed 존재) → user2 JWT 발급")
    void login_devFallback_withDevUserId_returnsRequestedUser() {
        ReflectionTestUtils.setField(sut, "devFallbackEnabled", true);
        given(kakaoOAuthPort.isConfigured()).willReturn(false);
        given(userRepository.findById(2L)).willReturn(Optional.of(userWithId(2L, "user2")));

        AuthResult result = sut.login("", "", 2L);

        assertThat(result.userId()).isEqualTo(2L);
        verify(jwtTokenProvider).issue(2L);
        verify(kakaoOAuthPort, never()).exchange(any(), any());
    }

    @Test
    @DisplayName("dev fallback + devUserId 미존재(99) → 기본 seed userId=1 로 폴백")
    void login_devFallback_devUserIdNotFound_fallsBackToSeed1() {
        ReflectionTestUtils.setField(sut, "devFallbackEnabled", true);
        given(kakaoOAuthPort.isConfigured()).willReturn(false);
        given(userRepository.findById(99L)).willReturn(Optional.empty());
        given(userRepository.findById(1L)).willReturn(Optional.of(userWithId(1L, "seed")));

        AuthResult result = sut.login("", "", 99L);

        assertThat(result.userId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("★P0 보안 — prod(devFallback=false) + devUserId=2 헤더 → 헤더 무시, KAKAO_AUTH_FAILED, findById 미호출")
    void login_prodMode_ignoresDevUserIdHeader_throws() {
        // devFallbackEnabled 기본 false (prod)
        given(kakaoOAuthPort.isConfigured()).willReturn(false);

        assertThatThrownBy(() -> sut.login("", "", 2L))
                .isInstanceOf(PillmateException.class)
                .satisfies(ex -> assertThat(((PillmateException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.KAKAO_AUTH_FAILED));

        verify(userRepository, never()).findById(any());
        verify(jwtTokenProvider, never()).issue(any());
    }

    @Test
    @DisplayName("prod(devFallback=false) + 실제 카카오 code → devUserId 헤더 무관 정상 카카오 로그인")
    void login_prodMode_realCode_ignoresDevUserId() {
        given(kakaoOAuthPort.isConfigured()).willReturn(true);
        given(kakaoOAuthPort.exchange("realcode", "redirect")).willReturn(KAKAO_PROFILE);
        given(userRepository.findByProviderAndExternalId(UserProvider.KAKAO, "kakao-id-1"))
                .willReturn(Optional.of(userWithId(7L, "홍길동")));

        AuthResult result = sut.login("realcode", "redirect", 2L);

        assertThat(result.userId()).isEqualTo(7L);  // 카카오 프로필 user, devUserId(2) 무시
    }

    // T-BE-WITHDRAW — 탈퇴 계정 재로그인 시 재활성화
    @Test
    @DisplayName("탈퇴 계정 재로그인 → reactivate(프로필 갱신) + save 후 정상 로그인")
    void login_withdrawnUser_reactivatesAndLogsIn() {
        given(kakaoOAuthPort.isConfigured()).willReturn(true);
        given(kakaoOAuthPort.exchange("code", "redirect")).willReturn(KAKAO_PROFILE);
        User withdrawn = userWithId(5L, "탈퇴한 사용자");
        withdrawn.withdraw(java.time.Instant.parse("2026-07-01T00:00:00Z"));
        given(userRepository.findByProviderAndExternalId(UserProvider.KAKAO, "kakao-id-1"))
                .willReturn(Optional.of(withdrawn));
        given(userRepository.save(withdrawn)).willReturn(withdrawn);

        AuthResult result = sut.login("code", "redirect");

        assertThat(result.userId()).isEqualTo(5L);
        assertThat(withdrawn.isWithdrawn()).isFalse();
        assertThat(withdrawn.getName()).isEqualTo("홍길동");
        assertThat(withdrawn.getEmail()).isEqualTo("hong@test.com");
        verify(userRepository).save(withdrawn);
    }

    private User userWithId(Long id, String name) {
        User user = User.dummy(name);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
