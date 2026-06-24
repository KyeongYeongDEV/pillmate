package com.pillmate.common.security;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@DisplayName("UserContextInterceptor — JWT 우선 / X-User-Id 폴백")
@ExtendWith(MockitoExtension.class)
class UserContextInterceptorTest {

    @Mock JwtTokenProvider jwtTokenProvider;

    private UserContextInterceptor sut;

    @BeforeEach
    void setUp() { sut = new UserContextInterceptor(jwtTokenProvider, true); }
    @AfterEach  void tearDown() { UserContext.clear(); }

    @Test
    @DisplayName("유효한 Bearer 토큰 → UserContext 에 userId 세팅")
    void preHandle_validBearer_setsUserContext() throws Exception {
        given(jwtTokenProvider.parseUserId("valid.token")).willReturn(99L);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid.token");

        boolean proceed = sut.preHandle(request, new MockHttpServletResponse(), null);

        assertThat(proceed).isTrue();
        assertThat(UserContext.get()).isEqualTo(99L);
    }

    @Test
    @DisplayName("위조 Bearer 토큰 → 401 응답, proceed=false")
    void preHandle_invalidBearer_returns401() throws Exception {
        given(jwtTokenProvider.parseUserId("bad.token"))
                .willThrow(new PillmateException(ErrorCode.INVALID_AUTH_TOKEN));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer bad.token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = sut.preHandle(request, response, null);

        assertThat(proceed).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    @DisplayName("Authorization 없고 X-User-Id 있음 → 폴백 userId 세팅")
    void preHandle_noBearer_xUserIdFallback() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "7");

        boolean proceed = sut.preHandle(request, new MockHttpServletResponse(), null);

        assertThat(proceed).isTrue();
        assertThat(UserContext.get()).isEqualTo(7L);
    }

    @Test
    @DisplayName("헤더 없음 → UserContext 미세팅, proceed=true")
    void preHandle_noHeaders_proceedsWithoutContext() throws Exception {
        boolean proceed = sut.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), null);

        assertThat(proceed).isTrue();
        assertThat(UserContext.get()).isNull();
    }

    @Test
    @DisplayName("jwtTokenProvider null — Bearer 헤더 무시하고 X-User-Id 폴백 동작")
    void preHandle_nullProvider_xUserIdFallback() throws Exception {
        UserContextInterceptor noJwt = new UserContextInterceptor(null, true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer some.token");
        request.addHeader("X-User-Id", "3");

        boolean proceed = noJwt.preHandle(request, new MockHttpServletResponse(), null);

        assertThat(proceed).isTrue();
        assertThat(UserContext.get()).isEqualTo(3L);
    }

    @Test
    @DisplayName("dev fallback 비활성 — JWT 없으면 401 (X-User-Id 무시)")
    void preHandle_devFallbackDisabled_noJwt_returns401() throws Exception {
        UserContextInterceptor noFallback = new UserContextInterceptor(jwtTokenProvider, false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "42");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = noFallback.preHandle(request, response, null);

        assertThat(proceed).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(UserContext.get()).isNull();
    }

    @Test
    @DisplayName("dev fallback 비활성 — 헤더 아무것도 없어도 401")
    void preHandle_devFallbackDisabled_noHeaders_returns401() throws Exception {
        UserContextInterceptor noFallback = new UserContextInterceptor(jwtTokenProvider, false);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = noFallback.preHandle(new MockHttpServletRequest(), response, null);

        assertThat(proceed).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    @DisplayName("dev fallback 비활성 — 비숫자 X-User-Id 포함해도 JWT 없으면 401")
    void preHandle_devFallbackDisabled_nonNumericXUserId_returns401() throws Exception {
        UserContextInterceptor noFallback = new UserContextInterceptor(jwtTokenProvider, false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "not-a-number");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = noFallback.preHandle(request, response, null);

        assertThat(proceed).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }
}
