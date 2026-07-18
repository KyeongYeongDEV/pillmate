package com.pillmate.user.presentation;

import com.pillmate.common.response.ApiResponse;
import com.pillmate.user.application.KakaoLoginService;
import com.pillmate.user.application.LoginCodeService;
import com.pillmate.user.application.dto.AuthResult;
import com.pillmate.user.presentation.dto.KakaoLoginRequest;
import com.pillmate.user.presentation.dto.LoginCodeExchangeRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@Slf4j
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
        log.info("KakaoCallback hit code_present={} error={}", code != null, error);
        if (error != null) {
            return redirect(deeplink + "?error=" + error);
        }
        try {
            return redirect(deeplink + "?loginCode=" + issueLoginCode(code));
        } catch (RuntimeException e) {
            // 콜백은 도달했으나 토큰교환/로그인 실패 — 401 JSON 대신 앱 딥링크로 에러 바운스(앱이 실패 안내 가능)
            log.error("KakaoCallback login failed: {}", e.getMessage());
            return redirect(deeplink + "?error=login_failed");
        }
    }

    private String issueLoginCode(String code) {
        AuthResult result = kakaoLoginService.login(code, kakaoRedirectUri);
        // JWT는 URL/로그에 노출하지 않음 — 단기(60초) 1회용 loginCode로 교환
        String loginCode = loginCodeService.generate(result);
        log.info("KakaoCallback success userId={} loginCode 발급", result.userId());
        return loginCode;
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
