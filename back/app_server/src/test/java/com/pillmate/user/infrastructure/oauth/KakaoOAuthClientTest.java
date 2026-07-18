package com.pillmate.user.infrastructure.oauth;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.user.application.port.KakaoProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

// T-BE-KAKAO-APPID — 네이티브 accessToken 이 PillMate 카카오 앱(app_id) 발급분인지 사전 검증
@DisplayName("KakaoOAuthClient — profileByAccessToken app_id 검증")
class KakaoOAuthClientTest {

    private static final String TOKEN_URL = "https://kauth.kakao.com/oauth/token";
    private static final String USER_INFO_URL = "https://kapi.kakao.com/v2/user/me";
    private static final String ACCESS_TOKEN_INFO_URL = "https://kapi.kakao.com/v1/user/access_token_info";
    private static final String EXPECTED_APP_ID = "1495467";

    @Test
    @DisplayName("access_token_info app_id 가 설정된 PillMate 앱과 일치 → 프로필 조회 진행")
    void profileByAccessToken_matchingAppId_returnsProfile() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KakaoOAuthClient client = new KakaoOAuthClient(
                builder, TOKEN_URL, USER_INFO_URL, ACCESS_TOKEN_INFO_URL, "client-id", "client-secret", EXPECTED_APP_ID);

        server.expect(requestTo(ACCESS_TOKEN_INFO_URL))
                .andExpect(header("Authorization", "Bearer valid-token"))
                .andRespond(withSuccess("""
                        {"id": 999, "expires_in": 3600, "app_id": 1495467}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(USER_INFO_URL))
                .andExpect(header("Authorization", "Bearer valid-token"))
                .andRespond(withSuccess("""
                        {"id": 12345, "kakao_account": {"email": "hong@test.com", "profile": {"nickname": "홍길동"}}}
                        """, MediaType.APPLICATION_JSON));

        KakaoProfile profile = client.profileByAccessToken("valid-token");

        assertThat(profile.kakaoId()).isEqualTo("12345");
        assertThat(profile.nickname()).isEqualTo("홍길동");
        server.verify();
    }

    @Test
    @DisplayName("access_token_info app_id 가 다른 카카오 앱 → KAKAO_AUTH_FAILED, 프로필 조회 미호출")
    void profileByAccessToken_mismatchedAppId_throwsAndSkipsProfileFetch() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KakaoOAuthClient client = new KakaoOAuthClient(
                builder, TOKEN_URL, USER_INFO_URL, ACCESS_TOKEN_INFO_URL, "client-id", "client-secret", EXPECTED_APP_ID);

        server.expect(requestTo(ACCESS_TOKEN_INFO_URL))
                .andRespond(withSuccess("""
                        {"id": 999, "expires_in": 3600, "app_id": 999999}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.profileByAccessToken("other-app-token"))
                .isInstanceOf(PillmateException.class)
                .satisfies(e -> assertThat(((PillmateException) e).getErrorCode())
                        .isEqualTo(ErrorCode.KAKAO_AUTH_FAILED));

        // user-info 엔드포인트에 대한 expectation 을 등록하지 않았으므로,
        // 호출되면 MockRestServiceServer 가 unexpected request 로 실패시킴 (미호출 검증)
        server.verify();
    }

    @Test
    @DisplayName("access_token_info 조회 자체가 401 실패(무효 토큰) → KAKAO_AUTH_FAILED")
    void profileByAccessToken_accessTokenInfoFails_throwsKakaoAuthFailed() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KakaoOAuthClient client = new KakaoOAuthClient(
                builder, TOKEN_URL, USER_INFO_URL, ACCESS_TOKEN_INFO_URL, "client-id", "client-secret", EXPECTED_APP_ID);

        server.expect(requestTo(ACCESS_TOKEN_INFO_URL))
                .andRespond(withStatus(UNAUTHORIZED));

        assertThatThrownBy(() -> client.profileByAccessToken("expired-token"))
                .isInstanceOf(PillmateException.class)
                .satisfies(e -> assertThat(((PillmateException) e).getErrorCode())
                        .isEqualTo(ErrorCode.KAKAO_AUTH_FAILED));

        server.verify();
    }

    @Test
    @DisplayName("kakao.app-id 미구성(blank) → app_id 검증 생략하고 바로 프로필 조회 (로컬/테스트 편의)")
    void profileByAccessToken_appIdNotConfigured_skipsVerification() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KakaoOAuthClient client = new KakaoOAuthClient(
                builder, TOKEN_URL, USER_INFO_URL, ACCESS_TOKEN_INFO_URL, "client-id", "client-secret", "");

        server.expect(requestTo(USER_INFO_URL))
                .andRespond(withSuccess("""
                        {"id": 12345, "kakao_account": {"email": "hong@test.com", "profile": {"nickname": "홍길동"}}}
                        """, MediaType.APPLICATION_JSON));

        KakaoProfile profile = client.profileByAccessToken("any-token");

        assertThat(profile.kakaoId()).isEqualTo("12345");
        server.verify();
    }

    @Test
    @DisplayName("exchange(code) 웹 콜백 경로는 app_id 재검증 없이 기존과 동일 동작 (client_secret 로 이미 앱 바인딩됨)")
    void exchange_webCallback_doesNotCallAccessTokenInfo() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KakaoOAuthClient client = new KakaoOAuthClient(
                builder, TOKEN_URL, USER_INFO_URL, ACCESS_TOKEN_INFO_URL, "client-id", "client-secret", EXPECTED_APP_ID);

        server.expect(requestTo(TOKEN_URL))
                .andRespond(withSuccess("""
                        {"access_token": "web-access-token"}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(USER_INFO_URL))
                .andRespond(withSuccess("""
                        {"id": 12345, "kakao_account": {"email": "hong@test.com", "profile": {"nickname": "홍길동"}}}
                        """, MediaType.APPLICATION_JSON));

        KakaoProfile profile = client.exchange("auth-code", "https://example.com/callback");

        assertThat(profile.kakaoId()).isEqualTo("12345");
        // access_token_info 에 대한 expectation 을 등록하지 않았으므로 호출됐다면 server.verify() 에서 실패
        server.verify();
    }
}
