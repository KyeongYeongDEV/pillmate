package com.pillmate.caregroup.presentation;

import com.pillmate.caregroup.application.CreateCareGroupUseCase;
import com.pillmate.caregroup.application.JoinGroupUseCase;
import com.pillmate.caregroup.application.dto.CreateGroupResponse;
import com.pillmate.caregroup.domain.model.MemberRole;
import com.pillmate.common.response.ApiResponse;
import com.pillmate.common.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/groups")
@RequiredArgsConstructor
public class CareGroupController {

    private final CreateCareGroupUseCase createCareGroupUseCase;
    private final JoinGroupUseCase joinGroupUseCase;

    @PostMapping
    public ResponseEntity<ApiResponse<CreateGroupResponse>> create(
            @RequestBody Map<String, String> body) {
        Long userId = UserContext.get();
        CreateGroupResponse response = createCareGroupUseCase.create(body.get("name"), userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/join/{code}")
    public ResponseEntity<ApiResponse<Map<String, Long>>> join(
            @PathVariable String code,
            @RequestParam(defaultValue = "PATIENT") String role) {
        Long userId = UserContext.get();
        Long groupId = joinGroupUseCase.join(code, userId, MemberRole.valueOf(role));
        return ResponseEntity.ok(ApiResponse.success(Map.of("groupId", groupId)));
    }
}
