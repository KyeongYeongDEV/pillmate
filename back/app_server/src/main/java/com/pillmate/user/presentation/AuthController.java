package com.pillmate.user.presentation;

import com.pillmate.common.response.ApiResponse;
import com.pillmate.user.application.KakaoLoginService;
import com.pillmate.user.application.dto.AuthResult;
import com.pillmate.user.presentation.dto.KakaoLoginRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final KakaoLoginService kakaoLoginService;

    @PostMapping("/kakao")
    public ResponseEntity<ApiResponse<AuthResult>> kakaoLogin(@RequestBody KakaoLoginRequest request) {
        AuthResult result = kakaoLoginService.login(
                request.code() != null ? request.code() : "",
                request.redirectUri() != null ? request.redirectUri() : "");
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
