package com.pillmate.caregroup.presentation;

import com.pillmate.caregroup.application.CreateCareGroupUseCase;
import com.pillmate.caregroup.application.GetGroupDetailUseCase;
import com.pillmate.caregroup.application.IssueInviteCodeUseCase;
import com.pillmate.caregroup.application.JoinGroupUseCase;
import com.pillmate.caregroup.application.LeaveGroupUseCase;
import com.pillmate.caregroup.application.ListMyGroupsUseCase;
import com.pillmate.caregroup.application.PinGroupUseCase;
import com.pillmate.caregroup.application.UnpinGroupUseCase;
import com.pillmate.caregroup.application.dto.CreateGroupResponse;
import com.pillmate.caregroup.domain.model.MemberRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("CareGroupController — join (POST + GET)")
@WebMvcTest(CareGroupController.class)
class CareGroupControllerTest {

    private static final String CODE = "INV12345";
    private static final Long USER_ID = 7L;
    private static final Long GROUP_ID = 42L;

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean CreateCareGroupUseCase createCareGroupUseCase;
    @MockitoBean JoinGroupUseCase joinGroupUseCase;
    @MockitoBean IssueInviteCodeUseCase issueInviteCodeUseCase;
    @MockitoBean ListMyGroupsUseCase listMyGroupsUseCase;
    @MockitoBean PinGroupUseCase pinGroupUseCase;
    @MockitoBean UnpinGroupUseCase unpinGroupUseCase;
    @MockitoBean GetGroupDetailUseCase getGroupDetailUseCase;
    @MockitoBean LeaveGroupUseCase leaveGroupUseCase;

    @Test
    @DisplayName("POST /groups → 200 + 생성된 그룹")
    void create_returns200() throws Exception {
        given(createCareGroupUseCase.create("우리가족", USER_ID))
                .willReturn(new CreateGroupResponse(GROUP_ID, "우리가족", "ADMIN", CODE));

        mockMvc.perform(post("/groups")
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateGroupRequestBody("우리가족"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groupId").value(GROUP_ID))
                .andExpect(jsonPath("$.data.name").value("우리가족"));
    }

    @Test
    @DisplayName("POST /groups name 공백이면 400 (INVALID_REQUEST)")
    void create_returns400_whenNameBlank() throws Exception {
        mockMvc.perform(post("/groups")
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateGroupRequestBody(""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PILL_040"));

        then(createCareGroupUseCase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("POST /groups name 누락이면 400 (INVALID_REQUEST)")
    void create_returns400_whenNameMissing() throws Exception {
        mockMvc.perform(post("/groups")
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PILL_040"));

        then(createCareGroupUseCase).shouldHaveNoInteractions();
    }

    private record CreateGroupRequestBody(String name) {}

    @Test
    @DisplayName("POST /groups/join/{code} → 200 + groupId")
    void joinPost_returns200() throws Exception {
        given(joinGroupUseCase.join(CODE, USER_ID, MemberRole.PATIENT)).willReturn(GROUP_ID);

        mockMvc.perform(post("/groups/join/" + CODE).header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groupId").value(GROUP_ID));
    }

    @Test
    @DisplayName("GET /groups/join/{code} → 200 + groupId (호환성)")
    void joinGet_returns200() throws Exception {
        given(joinGroupUseCase.join(CODE, USER_ID, MemberRole.PATIENT)).willReturn(GROUP_ID);

        mockMvc.perform(get("/groups/join/" + CODE).header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groupId").value(GROUP_ID));
    }

    @Test
    @DisplayName("DELETE /groups/{groupId}/membership → 200 + 본인 탈퇴 위임")
    void leave_returns200() throws Exception {
        mockMvc.perform(delete("/groups/" + GROUP_ID + "/membership").header("X-User-Id", USER_ID))
                .andExpect(status().isOk());

        then(leaveGroupUseCase).should().leave(GROUP_ID, USER_ID);
    }
}
