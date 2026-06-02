package com.pillmate.user.presentation;

import com.pillmate.common.response.ApiResponse;
import com.pillmate.common.security.UserContext;
import com.pillmate.user.application.RegisterPushTokenService;
import com.pillmate.user.domain.model.PushProvider;
import com.pillmate.user.presentation.dto.RegisterPushTokenRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users/me")
@RequiredArgsConstructor
public class UserController {

    private final RegisterPushTokenService registerPushTokenService;

    @PostMapping("/device-token")
    public ResponseEntity<ApiResponse<Void>> registerDeviceToken(
            @RequestBody @Valid RegisterPushTokenRequest request) {
        Long userId = UserContext.get();
        PushProvider provider = request.provider() == null
                ? PushProvider.EXPO
                : PushProvider.valueOf(request.provider());
        registerPushTokenService.register(userId, request.token(), provider);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
