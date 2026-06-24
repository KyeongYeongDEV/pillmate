package com.pillmate.user.presentation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pillmate.common.security.JwtTokenProvider;
import com.pillmate.user.application.KakaoLoginService;
import com.pillmate.user.application.dto.AuthResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@DisplayName("AuthController — POST /auth/kakao")
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean KakaoLoginService kakaoLoginService;
    @MockBean JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("POST /auth/kakao → 200 + token/userId/isNewUser/profile")
    void kakaoLogin_returns200() throws Exception {
        AuthResult result = new AuthResult("jwt.token.here", 1L, true,
                new AuthResult.ProfileInfo("홍길동", "hong@test.com", null));
        given(kakaoLoginService.login(any(), any())).willReturn(result);

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
        given(kakaoLoginService.login(any(), any())).willReturn(result);

        mockMvc.perform(post("/auth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"\",\"redirectUri\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(1));
    }
}
