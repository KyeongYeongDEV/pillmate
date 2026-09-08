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
import com.pillmate.caregroup.application.dto.GroupDetailResponse;
import com.pillmate.caregroup.application.dto.GroupMonthScheduleResponse;
import com.pillmate.caregroup.application.dto.InviteCodeResponse;
import com.pillmate.caregroup.application.dto.MyGroupSummary;
import com.pillmate.caregroup.application.dto.ShareSettingsView;
import com.pillmate.caregroup.domain.model.MemberRole;
import com.pillmate.caregroup.presentation.dto.CreateGroupRequest;
import com.pillmate.caregroup.presentation.dto.RenameGroupRequest;
import com.pillmate.caregroup.presentation.dto.UpdateShareSettingRequest;
import com.pillmate.common.response.ApiResponse;
import com.pillmate.common.security.UserContext;
import com.pillmate.notification.application.dto.NudgeResponse;
import com.pillmate.prescription.application.GetSharedPrescriptionUseCase;
import com.pillmate.prescription.application.dto.SharedPrescriptionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/groups")
@RequiredArgsConstructor
public class CareGroupController {

    private final CreateCareGroupUseCase createCareGroupUseCase;
    private final JoinGroupUseCase joinGroupUseCase;
    private final IssueInviteCodeUseCase issueInviteCodeUseCase;
    private final ListMyGroupsUseCase listMyGroupsUseCase;
    private final PinGroupUseCase pinGroupUseCase;
    private final UnpinGroupUseCase unpinGroupUseCase;
    private final RenameCareGroupService renameCareGroupService;
    private final GetGroupDetailUseCase getGroupDetailUseCase;
    private final LeaveGroupUseCase leaveGroupUseCase;
    private final MedicationShareService medicationShareService;
    private final SendMemberNudgeService sendMemberNudgeService;
    private final GetGroupMonthScheduleService getGroupMonthScheduleService;
    private final GetGroupDayScheduleService getGroupDayScheduleService;
    private final GetSharedPrescriptionUseCase getSharedPrescriptionUseCase;

    @PostMapping
    public ResponseEntity<ApiResponse<CreateGroupResponse>> create(
            @RequestBody @Valid CreateGroupRequest request) {
        Long userId = UserContext.get();
        CreateGroupResponse response = createCareGroupUseCase.create(request.name(), userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{groupId}/invite-codes")
    public ResponseEntity<ApiResponse<InviteCodeResponse>> issueInviteCode(
            @PathVariable Long groupId) {
        Long userId = UserContext.get();
        return ResponseEntity.ok(ApiResponse.success(issueInviteCodeUseCase.issue(groupId, userId)));
    }

    @PostMapping("/join/{code}")
    public ResponseEntity<ApiResponse<Map<String, Long>>> joinViaPost(
            @PathVariable String code,
            @RequestParam(defaultValue = "PATIENT") String role) {
        return join(code, role);
    }

    /**
     * @deprecated 호환성 유지용 — 새 클라이언트는 POST /groups/join/{code} 사용
     */
    @Deprecated
    @GetMapping("/join/{code}")
    public ResponseEntity<ApiResponse<Map<String, Long>>> join(
            @PathVariable String code,
            @RequestParam(defaultValue = "PATIENT") String role) {
        Long userId = UserContext.get();
        Long groupId = joinGroupUseCase.join(code, userId, MemberRole.valueOf(role));
        return ResponseEntity.ok(ApiResponse.success(Map.of("groupId", groupId)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<MyGroupSummary>>> listMyGroups() {
        Long userId = UserContext.get();
        return ResponseEntity.ok(ApiResponse.success(listMyGroupsUseCase.listMyGroups(userId)));
    }

    @PostMapping("/{groupId}/pin")
    public ResponseEntity<ApiResponse<Void>> pin(@PathVariable Long groupId) {
        Long userId = UserContext.get();
        pinGroupUseCase.pin(groupId, userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping("/{groupId}/pin")
    public ResponseEntity<ApiResponse<Void>> unpin(@PathVariable Long groupId) {
        Long userId = UserContext.get();
        unpinGroupUseCase.unpin(groupId, userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/{groupId}")
    public ResponseEntity<ApiResponse<GroupDetailResponse>> getDetail(@PathVariable Long groupId) {
        Long userId = UserContext.get();
        return ResponseEntity.ok(ApiResponse.success(getGroupDetailUseCase.detail(groupId, userId)));
    }

    @PatchMapping("/{groupId}")
    public ResponseEntity<ApiResponse<Void>> renameGroup(
            @PathVariable Long groupId,
            @RequestBody @Valid RenameGroupRequest request) {
        Long userId = UserContext.get();
        renameCareGroupService.rename(groupId, userId, request.name());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping("/{groupId}/membership")
    public ResponseEntity<ApiResponse<Void>> leave(@PathVariable Long groupId) {
        Long userId = UserContext.get();
        leaveGroupUseCase.leave(groupId, userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/{groupId}/share-settings")
    public ResponseEntity<ApiResponse<ShareSettingsView>> getShareSettings(@PathVariable Long groupId) {
        Long userId = UserContext.get();
        return ResponseEntity.ok(ApiResponse.success(medicationShareService.getShareSettings(groupId, userId)));
    }

    @PutMapping("/{groupId}/share-settings/members/{viewerUserId}")
    public ResponseEntity<ApiResponse<Void>> updateMemberShare(
            @PathVariable Long groupId,
            @PathVariable Long viewerUserId,
            @RequestBody @Valid UpdateShareSettingRequest request) {
        Long userId = UserContext.get();
        medicationShareService.updateMemberShare(groupId, userId, viewerUserId, request.enabled());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PutMapping("/{groupId}/share-settings/prescriptions/{prescriptionId}")
    public ResponseEntity<ApiResponse<Void>> updatePrescriptionShare(
            @PathVariable Long groupId,
            @PathVariable Long prescriptionId,
            @RequestBody @Valid UpdateShareSettingRequest request) {
        Long userId = UserContext.get();
        medicationShareService.updatePrescriptionShare(groupId, userId, prescriptionId, request.enabled());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{groupId}/members/{userId}/nudge")
    public ResponseEntity<ApiResponse<NudgeResponse>> nudgeMember(
            @PathVariable Long groupId, @PathVariable Long userId) {
        Long callerUserId = UserContext.get();
        NudgeResponse response = sendMemberNudgeService.nudge(groupId, userId, callerUserId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{groupId}/schedule/month")
    public ResponseEntity<ApiResponse<GroupMonthScheduleResponse>> getGroupMonthSchedule(
            @PathVariable Long groupId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return ResponseEntity.ok(ApiResponse.success(getGroupMonthScheduleService.execute(groupId, month)));
    }

    @GetMapping("/{groupId}/schedule/day")
    public ResponseEntity<ApiResponse<GroupDayScheduleResponse>> getGroupDaySchedule(
            @PathVariable Long groupId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.success(getGroupDayScheduleService.execute(groupId, date)));
    }

    @GetMapping("/{groupId}/prescriptions/{prescriptionId}")
    public ResponseEntity<ApiResponse<SharedPrescriptionResponse>> getSharedPrescription(
            @PathVariable Long groupId,
            @PathVariable Long prescriptionId) {
        Long userId = UserContext.get();
        return ResponseEntity.ok(ApiResponse.success(
                getSharedPrescriptionUseCase.execute(groupId, prescriptionId, userId)));
    }
}
