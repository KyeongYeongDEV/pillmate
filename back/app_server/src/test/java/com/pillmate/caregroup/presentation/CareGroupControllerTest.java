package com.pillmate.caregroup.presentation;

import com.pillmate.caregroup.application.CreateCareGroupUseCase;
import com.pillmate.caregroup.application.GetGroupDayScheduleService;
import com.pillmate.caregroup.application.GetGroupDetailUseCase;
import com.pillmate.caregroup.application.GetGroupMonthScheduleService;
import com.pillmate.caregroup.application.IssueInviteCodeUseCase;
import com.pillmate.caregroup.application.JoinGroupUseCase;
import com.pillmate.caregroup.application.LeaveGroupUseCase;
import com.pillmate.caregroup.application.ListMyGroupsUseCase;
import com.pillmate.caregroup.application.MedicationShareService;
import com.pillmate.caregroup.application.PinGroupUseCase;
import com.pillmate.caregroup.application.RenameCareGroupService;
import com.pillmate.caregroup.application.SendMemberNudgeService;
import com.pillmate.caregroup.application.UnpinGroupUseCase;
import com.pillmate.caregroup.application.dto.CreateGroupResponse;
import com.pillmate.caregroup.application.dto.GroupDayScheduleResponse;
import com.pillmate.caregroup.application.dto.GroupDayScheduleResponse.MemberDayView;
import com.pillmate.caregroup.application.dto.GroupMonthScheduleResponse;
import com.pillmate.caregroup.application.dto.GroupMonthScheduleResponse.GroupDayView;
import com.pillmate.caregroup.application.dto.GroupMonthScheduleResponse.MemberAdherenceView;
import com.pillmate.caregroup.application.dto.ShareSettingsView;
import com.pillmate.caregroup.application.dto.ShareSettingView;
import com.pillmate.caregroup.application.dto.ShareablePrescriptionView;
import com.pillmate.caregroup.domain.model.MemberRole;
import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.notification.application.dto.NudgeResponse;
import com.pillmate.prescription.application.GetSharedPrescriptionUseCase;
import com.pillmate.prescription.application.dto.SharedPrescriptionResponse;
import com.pillmate.prescription.domain.model.PrescriptionStatus;
import com.pillmate.schedule.application.dto.DayScheduleResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("CareGroupController — join (POST + GET)")
@WebMvcTest(CareGroupController.class)
class CareGroupControllerTest {

    private static final String CODE = "INV12345";
    private static final Long USER_ID = 7L;
    private static final Long GROUP_ID = 42L;
    private static final Long VIEWER_ID = 8L;
    private static final Long PRESCRIPTION_ID = 1L;

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean CreateCareGroupUseCase createCareGroupUseCase;
    @MockitoBean JoinGroupUseCase joinGroupUseCase;
    @MockitoBean IssueInviteCodeUseCase issueInviteCodeUseCase;
    @MockitoBean ListMyGroupsUseCase listMyGroupsUseCase;
    @MockitoBean PinGroupUseCase pinGroupUseCase;
    @MockitoBean UnpinGroupUseCase unpinGroupUseCase;
    @MockitoBean RenameCareGroupService renameCareGroupService;
    @MockitoBean GetGroupDetailUseCase getGroupDetailUseCase;
    @MockitoBean LeaveGroupUseCase leaveGroupUseCase;
    @MockitoBean MedicationShareService medicationShareService;
    @MockitoBean SendMemberNudgeService sendMemberNudgeService;
    @MockitoBean GetGroupMonthScheduleService getGroupMonthScheduleService;
    @MockitoBean GetGroupDayScheduleService getGroupDayScheduleService;
    @MockitoBean GetSharedPrescriptionUseCase getSharedPrescriptionUseCase;

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

    @Test
    @DisplayName("PATCH /groups/{groupId} → 200 + 이름 변경 위임")
    void renameGroup_returns200() throws Exception {
        mockMvc.perform(patch("/groups/" + GROUP_ID)
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RenameGroupRequestBody("새 가족 이름"))))
                .andExpect(status().isOk());

        then(renameCareGroupService).should().rename(GROUP_ID, USER_ID, "새 가족 이름");
    }

    @Test
    @DisplayName("PATCH /groups/{groupId} name 공백이면 400 (INVALID_REQUEST)")
    void renameGroup_returns400_whenNameBlank() throws Exception {
        mockMvc.perform(patch("/groups/" + GROUP_ID)
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RenameGroupRequestBody(" "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PILL_040"));

        then(renameCareGroupService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("PATCH /groups/{groupId} name 101자면 400 (INVALID_REQUEST)")
    void renameGroup_returns400_whenNameTooLong() throws Exception {
        String tooLong = "가".repeat(101);

        mockMvc.perform(patch("/groups/" + GROUP_ID)
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RenameGroupRequestBody(tooLong))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PILL_040"));

        then(renameCareGroupService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("PATCH /groups/{groupId} — 비구성원이면 403")
    void renameGroup_nonMember_returns403() throws Exception {
        org.mockito.BDDMockito.willThrow(new PillmateException(ErrorCode.GROUP_ACCESS_DENIED))
                .given(renameCareGroupService).rename(GROUP_ID, USER_ID, "새 가족 이름");

        mockMvc.perform(patch("/groups/" + GROUP_ID)
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RenameGroupRequestBody("새 가족 이름"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PILL_011"));
    }

    private record RenameGroupRequestBody(String name) {}

    @Test
    @DisplayName("GET /groups/{groupId}/share-settings → 200 + 구성원별·약봉투별 공유 설정 목록")
    void getShareSettings_returns200() throws Exception {
        given(medicationShareService.getShareSettings(GROUP_ID, USER_ID))
                .willReturn(new ShareSettingsView(
                        List.of(new ShareSettingView(VIEWER_ID, "아버지", "PATIENT", true)),
                        List.of(new ShareablePrescriptionView(
                                PRESCRIPTION_ID, "감기약", LocalDate.of(2026, 6, 1), true, PrescriptionStatus.ONGOING))));

        mockMvc.perform(get("/groups/" + GROUP_ID + "/share-settings").header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.members[0].userId").value(VIEWER_ID))
                .andExpect(jsonPath("$.data.members[0].name").value("아버지"))
                .andExpect(jsonPath("$.data.members[0].shared").value(true))
                .andExpect(jsonPath("$.data.prescriptions[0].prescriptionId").value(PRESCRIPTION_ID))
                .andExpect(jsonPath("$.data.prescriptions[0].label").value("감기약"))
                .andExpect(jsonPath("$.data.prescriptions[0].shared").value(true));
    }

    @Test
    @DisplayName("GET /groups/{groupId}/share-settings — 비멤버 요청자는 403")
    void getShareSettings_nonMember_returns403() throws Exception {
        given(medicationShareService.getShareSettings(GROUP_ID, USER_ID))
                .willThrow(new PillmateException(ErrorCode.GROUP_ACCESS_DENIED));

        mockMvc.perform(get("/groups/" + GROUP_ID + "/share-settings").header("X-User-Id", USER_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PILL_011"));
    }

    @Test
    @DisplayName("PUT /groups/{groupId}/share-settings/members/{viewerUserId} → 200")
    void updateMemberShare_returns200() throws Exception {
        mockMvc.perform(put("/groups/" + GROUP_ID + "/share-settings/members/" + VIEWER_ID)
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateShareSettingRequestBody(true))))
                .andExpect(status().isOk());

        then(medicationShareService).should().updateMemberShare(GROUP_ID, USER_ID, VIEWER_ID, true);
    }

    @Test
    @DisplayName("PUT /groups/{groupId}/share-settings/members/{viewerUserId} — 유효하지 않은 대상이면 400")
    void updateMemberShare_invalidTarget_returns400() throws Exception {
        org.mockito.BDDMockito.willThrow(new PillmateException(ErrorCode.MEDICATION_SHARE_INVALID_TARGET))
                .given(medicationShareService).updateMemberShare(GROUP_ID, USER_ID, VIEWER_ID, true);

        mockMvc.perform(put("/groups/" + GROUP_ID + "/share-settings/members/" + VIEWER_ID)
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateShareSettingRequestBody(true))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PILL_017"));
    }

    @Test
    @DisplayName("PUT /groups/{groupId}/share-settings/members/{viewerUserId} — 비멤버 요청자는 403")
    void updateMemberShare_nonMember_returns403() throws Exception {
        org.mockito.BDDMockito.willThrow(new PillmateException(ErrorCode.GROUP_ACCESS_DENIED))
                .given(medicationShareService).updateMemberShare(GROUP_ID, USER_ID, VIEWER_ID, true);

        mockMvc.perform(put("/groups/" + GROUP_ID + "/share-settings/members/" + VIEWER_ID)
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateShareSettingRequestBody(true))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PILL_011"));
    }

    @Test
    @DisplayName("PUT /groups/{groupId}/share-settings/prescriptions/{prescriptionId} → 200")
    void updatePrescriptionShare_returns200() throws Exception {
        mockMvc.perform(put("/groups/" + GROUP_ID + "/share-settings/prescriptions/" + PRESCRIPTION_ID)
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateShareSettingRequestBody(true))))
                .andExpect(status().isOk());

        then(medicationShareService).should().updatePrescriptionShare(GROUP_ID, USER_ID, PRESCRIPTION_ID, true);
    }

    @Test
    @DisplayName("PUT /groups/{groupId}/share-settings/prescriptions/{prescriptionId} — 남의 약봉투면 403")
    void updatePrescriptionShare_notOwnPrescription_returns403() throws Exception {
        org.mockito.BDDMockito.willThrow(new PillmateException(ErrorCode.PATIENT_ACCESS_DENIED))
                .given(medicationShareService).updatePrescriptionShare(GROUP_ID, USER_ID, PRESCRIPTION_ID, true);

        mockMvc.perform(put("/groups/" + GROUP_ID + "/share-settings/prescriptions/" + PRESCRIPTION_ID)
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateShareSettingRequestBody(true))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PILL_016"));
    }

    @Test
    @DisplayName("PUT /groups/{groupId}/share-settings/prescriptions/{prescriptionId} — 비멤버 요청자는 403")
    void updatePrescriptionShare_nonMember_returns403() throws Exception {
        org.mockito.BDDMockito.willThrow(new PillmateException(ErrorCode.GROUP_ACCESS_DENIED))
                .given(medicationShareService).updatePrescriptionShare(GROUP_ID, USER_ID, PRESCRIPTION_ID, true);

        mockMvc.perform(put("/groups/" + GROUP_ID + "/share-settings/prescriptions/" + PRESCRIPTION_ID)
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateShareSettingRequestBody(true))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PILL_011"));
    }

    @Test
    @DisplayName("PUT /groups/{groupId}/share-settings/prescriptions/{prescriptionId} — enabled 누락이면 400 (INVALID_REQUEST)")
    void updatePrescriptionShare_enabledMissing_returns400() throws Exception {
        mockMvc.perform(put("/groups/" + GROUP_ID + "/share-settings/prescriptions/" + PRESCRIPTION_ID)
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PILL_040"));

        then(medicationShareService).shouldHaveNoInteractions();
    }

    private record UpdateShareSettingRequestBody(Boolean enabled) {}

    @Test
    @DisplayName("POST /groups/{groupId}/members/{userId}/nudge → 200 + 위임 결과")
    void nudgeMember_returns200() throws Exception {
        given(sendMemberNudgeService.nudge(GROUP_ID, VIEWER_ID, USER_ID))
                .willReturn(new NudgeResponse(false));

        mockMvc.perform(post("/groups/" + GROUP_ID + "/members/" + VIEWER_ID + "/nudge")
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.alreadyNotified").value(false));
    }

    @Test
    @DisplayName("POST /groups/{groupId}/members/{userId}/nudge — 호출자 비멤버면 403")
    void nudgeMember_callerNotMember_returns403() throws Exception {
        given(sendMemberNudgeService.nudge(GROUP_ID, VIEWER_ID, USER_ID))
                .willThrow(new PillmateException(ErrorCode.GROUP_ACCESS_DENIED));

        mockMvc.perform(post("/groups/" + GROUP_ID + "/members/" + VIEWER_ID + "/nudge")
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PILL_011"));
    }

    @Test
    @DisplayName("POST /groups/{groupId}/members/{userId}/nudge — 대상이 자기자신/비멤버면 400")
    void nudgeMember_invalidTarget_returns400() throws Exception {
        given(sendMemberNudgeService.nudge(GROUP_ID, VIEWER_ID, USER_ID))
                .willThrow(new PillmateException(ErrorCode.NUDGE_TARGET_INVALID));

        mockMvc.perform(post("/groups/" + GROUP_ID + "/members/" + VIEWER_ID + "/nudge")
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PILL_044"));
    }

    @Test
    @DisplayName("POST /groups/{groupId}/members/{userId}/nudge — 놓친 복약 없으면 409")
    void nudgeMember_noOverdueDose_returns409() throws Exception {
        given(sendMemberNudgeService.nudge(GROUP_ID, VIEWER_ID, USER_ID))
                .willThrow(new PillmateException(ErrorCode.NUDGE_NO_OVERDUE_DOSE));

        mockMvc.perform(post("/groups/" + GROUP_ID + "/members/" + VIEWER_ID + "/nudge")
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PILL_045"));
    }

    @Test
    @DisplayName("GET /groups/{groupId}/schedule/month → 200 + 구성원별 날짜별 adherence")
    void getGroupMonthSchedule_returns200() throws Exception {
        YearMonth month = YearMonth.of(2026, 9);
        LocalDate day1 = LocalDate.of(2026, 9, 1);
        given(getGroupMonthScheduleService.execute(GROUP_ID, month))
                .willReturn(new GroupMonthScheduleResponse("2026-09", List.of(
                        new GroupDayView(day1, List.of(new MemberAdherenceView(USER_ID, "FULL"))))));

        mockMvc.perform(get("/groups/" + GROUP_ID + "/schedule/month")
                        .header("X-User-Id", USER_ID)
                        .param("month", "2026-09"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.month").value("2026-09"))
                .andExpect(jsonPath("$.data.days[0].date").value("2026-09-01"))
                .andExpect(jsonPath("$.data.days[0].members[0].userId").value(USER_ID))
                .andExpect(jsonPath("$.data.days[0].members[0].adherence").value("FULL"));
    }

    @Test
    @DisplayName("GET /groups/{groupId}/schedule/month — 호출자 비멤버면 403")
    void getGroupMonthSchedule_callerNotMember_returns403() throws Exception {
        YearMonth month = YearMonth.of(2026, 9);
        given(getGroupMonthScheduleService.execute(GROUP_ID, month))
                .willThrow(new PillmateException(ErrorCode.GROUP_ACCESS_DENIED));

        mockMvc.perform(get("/groups/" + GROUP_ID + "/schedule/month")
                        .header("X-User-Id", USER_ID)
                        .param("month", "2026-09"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PILL_011"));
    }

    @Test
    @DisplayName("GET /groups/{groupId}/schedule/month — month 형식이 잘못되면 500이 아니라 400")
    void getGroupMonthSchedule_malformedMonth_returns400() throws Exception {
        mockMvc.perform(get("/groups/" + GROUP_ID + "/schedule/month")
                        .header("X-User-Id", USER_ID)
                        .param("month", "2026-13"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PILL_040"));

        then(getGroupMonthScheduleService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("GET /groups/{groupId}/schedule/day → 200 + 구성원별 하루 스케줄")
    void getGroupDaySchedule_returns200() throws Exception {
        LocalDate date = LocalDate.of(2026, 9, 7);
        DayScheduleResponse schedule = new DayScheduleResponse(date, 2, 1, List.of());
        given(getGroupDayScheduleService.execute(GROUP_ID, date))
                .willReturn(new GroupDayScheduleResponse(date, List.of(
                        new MemberDayView(USER_ID, "아버지", schedule))));

        mockMvc.perform(get("/groups/" + GROUP_ID + "/schedule/day")
                        .header("X-User-Id", USER_ID)
                        .param("date", "2026-09-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.date").value("2026-09-07"))
                .andExpect(jsonPath("$.data.members[0].userId").value(USER_ID))
                .andExpect(jsonPath("$.data.members[0].name").value("아버지"))
                .andExpect(jsonPath("$.data.members[0].schedule.date").value("2026-09-07"))
                .andExpect(jsonPath("$.data.members[0].schedule.totalCount").value(2))
                .andExpect(jsonPath("$.data.members[0].schedule.doneCount").value(1));
    }

    @Test
    @DisplayName("GET /groups/{groupId}/schedule/day — 호출자 비멤버면 403")
    void getGroupDaySchedule_callerNotMember_returns403() throws Exception {
        LocalDate date = LocalDate.of(2026, 9, 7);
        given(getGroupDayScheduleService.execute(GROUP_ID, date))
                .willThrow(new PillmateException(ErrorCode.GROUP_ACCESS_DENIED));

        mockMvc.perform(get("/groups/" + GROUP_ID + "/schedule/day")
                        .header("X-User-Id", USER_ID)
                        .param("date", "2026-09-07"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PILL_011"));
    }

    @Test
    @DisplayName("GET /groups/{groupId}/schedule/day — date 형식이 잘못되면 500이 아니라 400")
    void getGroupDaySchedule_malformedDate_returns400() throws Exception {
        mockMvc.perform(get("/groups/" + GROUP_ID + "/schedule/day")
                        .header("X-User-Id", USER_ID)
                        .param("date", "2026-13-99"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PILL_040"));

        then(getGroupDayScheduleService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("GET /groups/{groupId}/prescriptions/{prescriptionId} → 200 + 공유 약 정보")
    void getSharedPrescription_returns200() throws Exception {
        SharedPrescriptionResponse.SharedDrugDetail drug = new SharedPrescriptionResponse.SharedDrugDetail(
                "메트포르민정", "메트포르민정500밀리그램", "KD-999",
                new BigDecimal("1.00"), "정", 3, 7,
                "https://img.test/m.png", List.of());
        given(getSharedPrescriptionUseCase.execute(GROUP_ID, PRESCRIPTION_ID, USER_ID))
                .willReturn(new SharedPrescriptionResponse(
                        PRESCRIPTION_ID, VIEWER_ID, LocalDate.of(2026, 6, 1), "복약 A",
                        PrescriptionStatus.ONGOING, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30),
                        15, 0.5, 0.9, List.of(drug)));

        mockMvc.perform(get("/groups/" + GROUP_ID + "/prescriptions/" + PRESCRIPTION_ID)
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ownerUserId").value(VIEWER_ID))
                .andExpect(jsonPath("$.data.drugs[0].matchedDrugName").value("메트포르민정500밀리그램"))
                .andExpect(jsonPath("$.data.drugs[0].matchedKdCode").value("KD-999"));
    }

    @Test
    @DisplayName("GET /groups/{groupId}/prescriptions/{prescriptionId} — 공유 권한 없으면 403")
    void getSharedPrescription_notGranted_returns403() throws Exception {
        given(getSharedPrescriptionUseCase.execute(GROUP_ID, PRESCRIPTION_ID, USER_ID))
                .willThrow(new PillmateException(ErrorCode.MEDICATION_SHARE_NOT_GRANTED));

        mockMvc.perform(get("/groups/" + GROUP_ID + "/prescriptions/" + PRESCRIPTION_ID)
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PILL_046"));
    }
}
