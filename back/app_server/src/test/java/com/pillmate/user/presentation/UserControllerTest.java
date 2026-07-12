package com.pillmate.user.presentation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.user.application.RegisterPushTokenService;
import com.pillmate.user.application.UpdateProfileService;
import com.pillmate.user.application.WithdrawUserService;
import com.pillmate.user.application.dto.UserProfileResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("UserController — PATCH /users/me (닉네임 변경)")
@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean RegisterPushTokenService registerPushTokenService;
    @MockitoBean WithdrawUserService withdrawUserService;
    @MockitoBean UpdateProfileService updateProfileService;

    @Test
    @DisplayName("PATCH /users/me → 200 + 변경된 프로필(name/email/profileUrl)")
    void updateProfile_returns200() throws Exception {
        given(updateProfileService.updateName(7L, "새이름"))
                .willReturn(new UserProfileResponse("새이름", "hong@example.com", "https://profile.img"));

        mockMvc.perform(patch("/users/me")
                        .header("X-User-Id", "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"새이름\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("새이름"))
                .andExpect(jsonPath("$.data.email").value("hong@example.com"))
                .andExpect(jsonPath("$.data.profileUrl").value("https://profile.img"));
    }

    @Test
    @DisplayName("PATCH /users/me name blank → 400 (Bean Validation)")
    void updateProfile_blankName_returns400() throws Exception {
        mockMvc.perform(patch("/users/me")
                        .header("X-User-Id", "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH /users/me name 21자 초과 → 400 (Bean Validation)")
    void updateProfile_tooLongName_returns400() throws Exception {
        String tooLong = "가".repeat(21);

        mockMvc.perform(patch("/users/me")
                        .header("X-User-Id", "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH /users/me 탈퇴 계정 → 401 PILL_092")
    void updateProfile_withdrawnAccount_returns401() throws Exception {
        given(updateProfileService.updateName(7L, "새이름"))
                .willThrow(new PillmateException(ErrorCode.ACCOUNT_WITHDRAWN));

        mockMvc.perform(patch("/users/me")
                        .header("X-User-Id", "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"새이름\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("PILL_092"));
    }
}
