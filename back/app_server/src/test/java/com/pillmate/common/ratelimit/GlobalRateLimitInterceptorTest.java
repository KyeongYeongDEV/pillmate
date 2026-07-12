package com.pillmate.common.ratelimit;

import com.pillmate.common.security.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("GlobalRateLimitInterceptor — 전역 per-user throttle (fail-open)")
@ExtendWith(MockitoExtension.class)
class GlobalRateLimitInterceptorTest {

    private static final int RPM = 120;
    private static final Long USER_ID = 7L;

    @Mock RateLimiterPort rateLimiterPort;

    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private GlobalRateLimitInterceptor interceptor(boolean enabled) {
        return new GlobalRateLimitInterceptor(rateLimiterPort, enabled, RPM);
    }

    @Test
    @DisplayName("한도 내 — checkAndIncrementPerMinute(userId,'req',120) 호출 후 통과")
    void withinLimit_passesAndCounts() {
        UserContext.set(USER_ID);

        boolean result = interceptor(true).preHandle(request, response, new Object());

        assertThat(result).isTrue();
        verify(rateLimiterPort).checkAndIncrementPerMinute(USER_ID, "req", RPM);
    }

    @Test
    @DisplayName("한도 초과 — RateLimitExceededException 전파 (GlobalExceptionHandler 가 429 매핑)")
    void exceedingLimit_throws() {
        UserContext.set(USER_ID);
        willThrow(new RateLimitExceededException())
                .given(rateLimiterPort).checkAndIncrementPerMinute(anyLong(), anyString(), anyInt());

        assertThatThrownBy(() -> interceptor(true).preHandle(request, response, new Object()))
                .isInstanceOf(RateLimitExceededException.class)
                .hasFieldOrPropertyWithValue("errorCode",
                        com.pillmate.common.exception.ErrorCode.RATE_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("비인증(UserContext null) — 계층① 담당, 카운트 없이 통과")
    void noUserContext_passesWithoutCount() {
        boolean result = interceptor(true).preHandle(request, response, new Object());

        assertThat(result).isTrue();
        verifyNoInteractions(rateLimiterPort);
    }

    @Test
    @DisplayName("Redis 장애 — fail-open, 요청 통과 (가용성 > 완벽 방어)")
    void redisDown_failOpen() {
        UserContext.set(USER_ID);
        willThrow(new RedisConnectionFailureException("redis down"))
                .given(rateLimiterPort).checkAndIncrementPerMinute(anyLong(), anyString(), anyInt());

        boolean result = interceptor(true).preHandle(request, response, new Object());

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("disabled(토글 off) — 카운트 없이 통과")
    void disabled_passesWithoutCount() {
        UserContext.set(USER_ID);

        boolean result = interceptor(false).preHandle(request, response, new Object());

        assertThat(result).isTrue();
        verify(rateLimiterPort, never()).checkAndIncrementPerMinute(anyLong(), eq("req"), anyInt());
    }
}
