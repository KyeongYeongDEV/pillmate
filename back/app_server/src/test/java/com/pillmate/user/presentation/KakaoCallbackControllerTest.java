package com.pillmate.user.presentation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.common.security.JwtTokenProvider;
import com.pillmate.user.application.KakaoLoginService;
import com.pillmate.user.application.LoginCodeService;
import com.pillmate.user.application.dto.AuthResult;
import com.pillmate.user.presentation.dto.KakaoNativeLoginRequest;
import com.pillmate.user.presentation.dto.LoginCodeExchangeRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@TestPropertySource(properties = {
        "kakao.redirect-uri=https://example.com/api/v1/auth/kakao/callback",
        "app.deeplink=pillmate://oauth/kakao"
})
@DisplayName("GET /auth/kakao/callback — 카카오 HTTPS 콜백 + 딥링크 바운스")
class KakaoCallbackControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean KakaoLoginService kakaoLoginService;
    @MockBean LoginCodeService loginCodeService;
    @MockBean JwtTokenProvider jwtTokenProvider;

    private final AuthResult sampleResult = new AuthResult(
            "jwt.token.here", 42L, true,
            new AuthResult.ProfileInfo("홍길동", "user@test.com", null));

    @Test
    @DisplayName("code 파라미터 → login() → loginCode 포함 302 리디렉션 (JWT URL 미노출)")
    void callback_withCode_redirectsWithLoginCodeNotJwt() throws Exception {
        given(kakaoLoginService.login("auth-code", "https://example.com/api/v1/auth/kakao/callback"))
                .willReturn(sampleResult);
        given(loginCodeService.generate(sampleResult)).willReturn("one-time-code-uuid");

        mockMvc.perform(get("/auth/kakao/callback").param("code", "auth-code"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("pillmate://oauth/kakao")))
                .andExpect(header().string("Location", containsString("loginCode=one-time-code-uuid")))
                // JWT는 URL에 절대 노출 금지
                .andExpect(header().string("Location", not(containsString("token="))));
    }

    @Test
    @DisplayName("error 파라미터 → 302 딥링크(error 포함), loginService 호출 없음")
    void callback_withError_redirectsWithError() throws Exception {
        mockMvc.perform(get("/auth/kakao/callback").param("error", "access_denied"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("pillmate://oauth/kakao")))
                .andExpect(header().string("Location", containsString("error=access_denied")));
    }

    @Test
    @DisplayName("code 정상인데 login() 실패 → 401 JSON 아닌 302 딥링크(error=login_failed) 바운스")
    void callback_loginFails_redirectsWithLoginFailedNot401() throws Exception {
        given(kakaoLoginService.login("bad-code", "https://example.com/api/v1/auth/kakao/callback"))
                .willThrow(new PillmateException(ErrorCode.KAKAO_AUTH_FAILED));

        mockMvc.perform(get("/auth/kakao/callback").param("code", "bad-code"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("pillmate://oauth/kakao")))
                .andExpect(header().string("Location", containsString("error=login_failed")));
    }

    @Test
    @DisplayName("/auth/kakao/callback 는 JWT 없이 접근 가능 (인증 예외 경로)")
    void callback_noAuthHeader_isPublicEndpoint() throws Exception {
        given(kakaoLoginService.login(any(), any())).willReturn(sampleResult);
        given(loginCodeService.generate(any())).willReturn("code");

        mockMvc.perform(get("/auth/kakao/callback").param("code", "x"))
                .andExpect(status().isFound());
    }

    @Test
    @DisplayName("POST /auth/kakao/exchange {loginCode} → 200 + JWT (URL 비노출 교환)")
    void exchange_validLoginCode_returnsJwt() throws Exception {
        given(loginCodeService.exchange("one-time-code-uuid")).willReturn(sampleResult);

        mockMvc.perform(post("/auth/kakao/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginCodeExchangeRequest("one-time-code-uuid"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").value("jwt.token.here"))
                .andExpect(jsonPath("$.data.userId").value(42))
                .andExpect(jsonPath("$.data.isNewUser").value(true));
    }

    // T-BE-KAKAO-NATIVE — 네이티브 SDK accessToken 검증 로그인
    @Test
    @DisplayName("POST /auth/kakao/native {accessToken} 유효 → 200 + JWT (웹 콜백과 동일 응답 스키마)")
    void nativeLogin_validAccessToken_returns200WithJwt() throws Exception {
        given(kakaoLoginService.loginWithAccessToken("valid-native-token")).willReturn(sampleResult);

        mockMvc.perform(post("/auth/kakao/native")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new KakaoNativeLoginRequest("valid-native-token"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").value("jwt.token.here"))
                .andExpect(jsonPath("$.data.userId").value(42))
                .andExpect(jsonPath("$.data.isNewUser").value(true));
    }

    @Test
    @DisplayName("POST /auth/kakao/native accessToken 무효 → 401 KAKAO_AUTH_FAILED")
    void nativeLogin_invalidAccessToken_returns401() throws Exception {
        given(kakaoLoginService.loginWithAccessToken("invalid-token"))
                .willThrow(new PillmateException(ErrorCode.KAKAO_AUTH_FAILED));

        mockMvc.perform(post("/auth/kakao/native")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new KakaoNativeLoginRequest("invalid-token"))))
                .andExpect(status().isUnauthorized());
    }
}
