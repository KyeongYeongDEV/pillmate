package com.pillmate.user.presentation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pillmate.common.security.JwtTokenProvider;
import com.pillmate.user.application.KakaoLoginService;
import com.pillmate.user.application.LoginCodeService;
import com.pillmate.user.application.RefreshTokenService;
import com.pillmate.user.application.dto.AuthResult;
import com.pillmate.user.application.dto.RefreshTokenResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
// dev fallback 강제 비활성 — /auth/refresh 인증 요구를 환경변수(PILLMATE_DEV_FALLBACK)와 무관하게 결정적으로 검증
@TestPropertySource(properties = "pillmate.auth.dev-fallback-enabled=false")
@DisplayName("AuthController — POST /auth/kakao, POST /auth/refresh")
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean KakaoLoginService kakaoLoginService;
    @MockBean LoginCodeService loginCodeService;
    @MockBean RefreshTokenService refreshTokenService;
    @MockBean JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("POST /auth/kakao → 200 + token/userId/isNewUser/profile")
    void kakaoLogin_returns200() throws Exception {
        AuthResult result = new AuthResult("jwt.token.here", 1L, true,
                new AuthResult.ProfileInfo("홍길동", "hong@test.com", null));
        given(kakaoLoginService.login(any(), any(), any())).willReturn(result);

        mockMvc.perform(post("/auth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"test-code\",\"redirectUri\":\"https://app\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").value("jwt.token.here"))
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.isNewUser").value(true))
                .andExpect(jsonPath("$.data.profile.name").value("홍길동"));
    }

    @Test
    @DisplayName("POST /auth/kakao dev fallback(빈 code) → 200")
    void kakaoLogin_emptyCode_returns200() throws Exception {
        AuthResult result = new AuthResult("seed.jwt", 1L, false,
                new AuthResult.ProfileInfo("seed", null, null));
        given(kakaoLoginService.login(any(), any(), any())).willReturn(result);

        mockMvc.perform(post("/auth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"\",\"redirectUri\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(1));
    }

    @Test
    @DisplayName("POST /auth/kakao + X-Dev-User-Id:2 헤더 → service 에 devUserId=2 전달")
    void kakaoLogin_withDevUserIdHeader_passesToService() throws Exception {
        AuthResult result = new AuthResult("u2.jwt", 2L, false,
                new AuthResult.ProfileInfo("user2", null, null));
        given(kakaoLoginService.login(any(), any(), eq(2L))).willReturn(result);

        mockMvc.perform(post("/auth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Dev-User-Id", "2")
                        .content("{\"code\":\"\",\"redirectUri\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(2));

        then(kakaoLoginService).should().login(any(), any(), eq(2L));
    }

    @Test
    @DisplayName("POST /auth/kakao 헤더 없음 → service 에 devUserId=null 전달 (기존 흐름)")
    void kakaoLogin_noHeader_passesNullDevUserId() throws Exception {
        AuthResult result = new AuthResult("seed.jwt", 1L, false,
                new AuthResult.ProfileInfo("seed", null, null));
        given(kakaoLoginService.login(any(), any(), any())).willReturn(result);

        mockMvc.perform(post("/auth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"\",\"redirectUri\":\"\"}"))
                .andExpect(status().isOk());

        then(kakaoLoginService).should().login(any(), any(), isNull());
    }

    @Test
    @DisplayName("POST /auth/refresh 유효한 Bearer 토큰 → 200 + 새 토큰")
    void refresh_validBearer_returns200WithNewToken() throws Exception {
        given(jwtTokenProvider.parseUserId("valid.token")).willReturn(5L);
        given(refreshTokenService.refresh(5L)).willReturn(new RefreshTokenResponse("new.jwt.token"));

        mockMvc.perform(post("/auth/refresh").header("Authorization", "Bearer valid.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").value("new.jwt.token"));

        then(refreshTokenService).should().refresh(5L);
    }

    @Test
    @DisplayName("POST /auth/refresh Authorization 헤더 없음 → 401 (인증 인터셉터에서 차단, 서비스 미호출)")
    void refresh_noAuthHeader_returns401() throws Exception {
        mockMvc.perform(post("/auth/refresh"))
                .andExpect(status().isUnauthorized());

        then(refreshTokenService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("POST /auth/refresh 무효 토큰 → 401 (인증 인터셉터에서 차단, 서비스 미호출)")
    void refresh_invalidBearer_returns401() throws Exception {
        given(jwtTokenProvider.parseUserId("bad.token"))
                .willThrow(new com.pillmate.common.exception.PillmateException(
                        com.pillmate.common.exception.ErrorCode.INVALID_AUTH_TOKEN));

        mockMvc.perform(post("/auth/refresh").header("Authorization", "Bearer bad.token"))
                .andExpect(status().isUnauthorized());

        then(refreshTokenService).shouldHaveNoInteractions();
    }

    // 회귀 검증 — /auth/refresh 신설로 UserContextInterceptor 제외 패턴을 "/auth/**" → "/auth/kakao/**" 로 좁혔다.
    // 카카오 로그인 진입점은 여전히 무인증으로 컨트롤러까지 도달해야 한다 (dev-fallback=false 로 강제해 확실히 검증).
    @Test
    @DisplayName("회귀: dev-fallback 비활성이어도 POST /auth/kakao 는 무인증 접근 가능")
    void kakaoLogin_stillPublicWhenDevFallbackDisabled() throws Exception {
        AuthResult result = new AuthResult("jwt.token.here", 1L, true,
                new AuthResult.ProfileInfo("홍길동", "hong@test.com", null));
        given(kakaoLoginService.login(any(), any(), any())).willReturn(result);

        mockMvc.perform(post("/auth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"test-code\",\"redirectUri\":\"https://app\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").value("jwt.token.here"));
    }

    @Test
    @DisplayName("회귀: dev-fallback 비활성이어도 POST /auth/kakao/native 는 무인증 접근 가능")
    void kakaoNativeLogin_stillPublicWhenDevFallbackDisabled() throws Exception {
        AuthResult result = new AuthResult("native.jwt", 2L, false,
                new AuthResult.ProfileInfo("사용자", null, null));
        given(kakaoLoginService.loginWithAccessToken("native-token")).willReturn(result);

        mockMvc.perform(post("/auth/kakao/native")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessToken\":\"native-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").value("native.jwt"));
    }
}
