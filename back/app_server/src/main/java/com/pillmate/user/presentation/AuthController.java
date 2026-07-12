package com.pillmate.user.presentation;

import com.pillmate.common.response.ApiResponse;
import com.pillmate.user.application.KakaoLoginService;
import com.pillmate.user.application.LoginCodeService;
import com.pillmate.user.application.dto.AuthResult;
import com.pillmate.user.presentation.dto.KakaoLoginRequest;
import com.pillmate.user.presentation.dto.LoginCodeExchangeRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final KakaoLoginService kakaoLoginService;
    private final LoginCodeService loginCodeService;
    @Value("${kakao.redirect-uri:}")
    private final String kakaoRedirectUri;
    @Value("${app.deeplink:pillmate://oauth/kakao}")
    private final String deeplink;

    @PostMapping("/kakao")
    public ResponseEntity<ApiResponse<AuthResult>> kakaoLogin(
            @RequestBody KakaoLoginRequest request,
            @RequestHeader(value = "X-Dev-User-Id", required = false) Long devUserId) {
        AuthResult result = kakaoLoginService.login(
                request.code() != null ? request.code() : "",
                request.redirectUri() != null ? request.redirectUri() : "",
                devUserId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/kakao/callback")
    public ResponseEntity<Void> kakaoCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String error) {
        if (error != null) {
            return redirect(deeplink + "?error=" + error);
        }
        AuthResult result = kakaoLoginService.login(code, kakaoRedirectUri);
        // JWT는 URL/로그에 노출하지 않음 — 단기(60초) 1회용 loginCode로 교환
        String loginCode = loginCodeService.generate(result);
        return redirect(deeplink + "?loginCode=" + loginCode);
    }

    @PostMapping("/kakao/exchange")
    public ResponseEntity<ApiResponse<AuthResult>> exchangeLoginCode(
            @RequestBody LoginCodeExchangeRequest request) {
        AuthResult result = loginCodeService.exchange(request.loginCode());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    private ResponseEntity<Void> redirect(String location) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(location))
                .build();
    }
}
