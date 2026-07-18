package com.pillmate.user.infrastructure.oauth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.user.application.port.KakaoOAuthPort;
import com.pillmate.user.application.port.KakaoProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
public class KakaoOAuthClient implements KakaoOAuthPort {

    private final RestClient restClient;
    private final String tokenUrl;
    private final String userInfoUrl;
    private final String accessTokenInfoUrl;
    private final String clientId;
    private final String clientSecret;
    private final Long expectedAppId;

    public KakaoOAuthClient(
            RestClient.Builder builder,
            @Value("${kakao.token-url:https://kauth.kakao.com/oauth/token}") String tokenUrl,
            @Value("${kakao.user-info-url:https://kapi.kakao.com/v2/user/me}") String userInfoUrl,
            @Value("${kakao.access-token-info-url:https://kapi.kakao.com/v1/user/access_token_info}") String accessTokenInfoUrl,
            @Value("${kakao.client-id:}") String clientId,
            @Value("${kakao.client-secret:}") String clientSecret,
            @Value("${kakao.app-id:}") String appId) {
        this.restClient = builder.build();
        this.tokenUrl = tokenUrl;
        this.userInfoUrl = userInfoUrl;
        this.accessTokenInfoUrl = accessTokenInfoUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.expectedAppId = parseAppId(appId);
    }

    @Override
    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank();
    }

    @Override
    public KakaoProfile exchange(String code, String redirectUri) {
        String accessToken = fetchAccessToken(code, redirectUri);
        return fetchUserInfo(accessToken);
    }

    // 네이티브 SDK accessToken 은 클라이언트가 카카오와 직접 교환한 토큰이라 발급 앱을 신뢰할 수 없음 —
    // 프로필 조회 전 access_token_info 로 PillMate 앱(app_id) 발급분인지 검증 (타 카카오앱 토큰 치환 방지).
    // 웹 콜백(exchange)은 client_id+client_secret 로 이미 앱이 바인딩돼 재검증이 불필요해 대상에서 제외.
    @Override
    public KakaoProfile profileByAccessToken(String accessToken) {
        verifyAppId(accessToken);
        return fetchUserInfo(accessToken);
    }

    private void verifyAppId(String accessToken) {
        if (expectedAppId == null) {
            return;
        }
        try {
            AccessTokenInfoResponse info = restClient.get()
                    .uri(accessTokenInfoUrl)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(AccessTokenInfoResponse.class);
            if (info == null || !expectedAppId.equals(info.appId())) {
                log.warn("Kakao access_token_info app_id mismatch (expected={})", expectedAppId);
                throw new PillmateException(ErrorCode.KAKAO_AUTH_FAILED);
            }
        } catch (RestClientException e) {
            log.error("Kakao access_token_info verification failed: {}", e.getMessage());
            throw new PillmateException(ErrorCode.KAKAO_AUTH_FAILED);
        }
    }

    private static Long parseAppId(String appId) {
        if (appId == null || appId.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(appId.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("kakao.app-id 설정값이 숫자가 아닙니다: " + appId, e);
        }
    }

    private String fetchAccessToken(String code, String redirectUri) {
        try {
            KakaoTokenResponse response = restClient.post()
                    .uri(tokenUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(buildTokenParams(code, redirectUri))
                    .retrieve()
                    .body(KakaoTokenResponse.class);
            if (response == null || response.accessToken() == null) {
                throw new PillmateException(ErrorCode.KAKAO_AUTH_FAILED);
            }
            return response.accessToken();
        } catch (RestClientResponseException e) {
            // 카카오 4xx/5xx — error/error_description(KOExxx) 로 B(토큰교환실패) 원인 확정. 응답 바디에 시크릿 없음.
            log.error("Kakao token exchange failed status={} body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new PillmateException(ErrorCode.KAKAO_AUTH_FAILED);
        } catch (RestClientException e) {
            log.error("Kakao token exchange failed: {}", e.getMessage());
            throw new PillmateException(ErrorCode.KAKAO_AUTH_FAILED);
        }
    }

    private KakaoProfile fetchUserInfo(String accessToken) {
        try {
            KakaoUserInfoResponse info = restClient.get()
                    .uri(userInfoUrl)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(KakaoUserInfoResponse.class);
            if (info == null) {
                throw new PillmateException(ErrorCode.KAKAO_AUTH_FAILED);
            }
            return toProfile(info);
        } catch (RestClientException e) {
            log.error("Kakao user-info fetch failed: {}", e.getMessage());
            throw new PillmateException(ErrorCode.KAKAO_AUTH_FAILED);
        }
    }

    private MultiValueMap<String, String> buildTokenParams(String code, String redirectUri) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", clientId);
        params.add("redirect_uri", redirectUri);
        params.add("code", code);
        if (clientSecret != null && !clientSecret.isBlank()) {
            params.add("client_secret", clientSecret);
        }
        return params;
    }

    private KakaoProfile toProfile(KakaoUserInfoResponse info) {
        String nickname = null;
        String email = null;
        String profileImageUrl = null;
        if (info.kakaoAccount() != null) {
            email = info.kakaoAccount().email();
            if (info.kakaoAccount().profile() != null) {
                nickname = info.kakaoAccount().profile().nickname();
                profileImageUrl = info.kakaoAccount().profile().profileImageUrl();
            }
        }
        return new KakaoProfile(String.valueOf(info.id()), nickname, email, profileImageUrl);
    }

    // 카카오 응답 필드는 문서에 없는 값(token_type 등)도 포함될 수 있어 미지 필드 무시 — strict 파싱 실패 방지
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KakaoTokenResponse(@JsonProperty("access_token") String accessToken) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AccessTokenInfoResponse(
            Long id,
            @JsonProperty("expires_in") Integer expiresIn,
            @JsonProperty("app_id") Long appId) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KakaoUserInfoResponse(
            Long id,
            @JsonProperty("kakao_account") KakaoAccount kakaoAccount) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KakaoAccount(
            String email,
            KakaoAccountProfile profile) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KakaoAccountProfile(
            String nickname,
            @JsonProperty("profile_image_url") String profileImageUrl) {}
}
