package com.pillmate.caregroup.presentation;

import com.pillmate.caregroup.application.CreateCareGroupUseCase;
import com.pillmate.caregroup.application.IssueInviteCodeUseCase;
import com.pillmate.caregroup.application.JoinGroupUseCase;
import com.pillmate.caregroup.application.ListMyGroupsUseCase;
import com.pillmate.caregroup.application.PinGroupUseCase;
import com.pillmate.caregroup.application.UnpinGroupUseCase;
import com.pillmate.caregroup.application.dto.CreateGroupResponse;
import com.pillmate.caregroup.application.dto.InviteCodeResponse;
import com.pillmate.caregroup.application.dto.MyGroupItem;
import com.pillmate.caregroup.domain.model.MemberRole;
import com.pillmate.common.response.ApiResponse;
import com.pillmate.common.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    @PostMapping
    public ResponseEntity<ApiResponse<CreateGroupResponse>> create(
            @RequestBody Map<String, String> body) {
        Long userId = UserContext.get();
        CreateGroupResponse response = createCareGroupUseCase.create(body.get("name"), userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{groupId}/invite-codes")
    public ResponseEntity<ApiResponse<InviteCodeResponse>> issueInviteCode(
            @PathVariable Long groupId) {
        Long userId = UserContext.get();
        return ResponseEntity.ok(ApiResponse.success(issueInviteCodeUseCase.issue(groupId, userId)));
    }

    @GetMapping("/join/{code}")
    public ResponseEntity<ApiResponse<Map<String, Long>>> join(
            @PathVariable String code,
            @RequestParam(defaultValue = "PATIENT") String role) {
        Long userId = UserContext.get();
        Long groupId = joinGroupUseCase.join(code, userId, MemberRole.valueOf(role));
        return ResponseEntity.ok(ApiResponse.success(Map.of("groupId", groupId)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<MyGroupItem>>> listMyGroups() {
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
}
