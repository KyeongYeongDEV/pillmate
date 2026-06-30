package com.pillmate.user.presentation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pillmate.common.security.JwtTokenProvider;
import com.pillmate.user.application.KakaoLoginService;
import com.pillmate.user.application.LoginCodeService;
import com.pillmate.user.application.dto.AuthResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@DisplayName("AuthController — POST /auth/kakao")
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean KakaoLoginService kakaoLoginService;
    @MockBean LoginCodeService loginCodeService;
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
}
