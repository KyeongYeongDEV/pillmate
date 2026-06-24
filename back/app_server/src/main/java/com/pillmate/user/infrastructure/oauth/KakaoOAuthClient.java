package com.pillmate.user.infrastructure.oauth;

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

@Slf4j
@Component
public class KakaoOAuthClient implements KakaoOAuthPort {

    private final RestClient restClient;
    private final String tokenUrl;
    private final String userInfoUrl;
    private final String clientId;
    private final String clientSecret;

    public KakaoOAuthClient(
            RestClient.Builder builder,
            @Value("${kakao.token-url:https://kauth.kakao.com/oauth/token}") String tokenUrl,
            @Value("${kakao.user-info-url:https://kapi.kakao.com/v2/user/me}") String userInfoUrl,
            @Value("${kakao.client-id:}") String clientId,
            @Value("${kakao.client-secret:}") String clientSecret) {
        this.restClient = builder.build();
        this.tokenUrl = tokenUrl;
        this.userInfoUrl = userInfoUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
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

    private record KakaoTokenResponse(@JsonProperty("access_token") String accessToken) {}

    private record KakaoUserInfoResponse(
            Long id,
            @JsonProperty("kakao_account") KakaoAccount kakaoAccount) {}

    private record KakaoAccount(
            String email,
            KakaoAccountProfile profile) {}

    private record KakaoAccountProfile(
            String nickname,
            @JsonProperty("profile_image_url") String profileImageUrl) {}
}
