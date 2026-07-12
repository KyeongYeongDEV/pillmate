package com.pillmate.common.ratelimit;

import com.pillmate.common.security.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 전역 per-user 요청 throttle (계층 ②). UserContextInterceptor 다음에 등록되어 userId 를 읽는다.
 *
 * Redis 장애 시 fail-open — rate limiter 가 서비스 전체를 막으면 안 되므로 통과시키고 warn 로그만 남긴다
 * (가용성 > 완벽 방어). 한도 초과는 RateLimitExceededException → GlobalExceptionHandler 가 429 매핑.
 */
@Slf4j
public class GlobalRateLimitInterceptor implements HandlerInterceptor {

    private static final String ACTION = "req";

    private final RateLimiterPort rateLimiterPort;
    private final boolean enabled;
    private final int perMinuteLimit;

    public GlobalRateLimitInterceptor(RateLimiterPort rateLimiterPort, boolean enabled, int perMinuteLimit) {
        this.rateLimiterPort = rateLimiterPort;
        this.enabled = enabled;
        this.perMinuteLimit = perMinuteLimit;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!enabled) {
            return true;
        }
        Long userId = UserContext.get();
        if (userId == null) {
            return true;
        }
        enforceLimit(userId);
        return true;
    }

    private void enforceLimit(Long userId) {
        try {
            rateLimiterPort.checkAndIncrementPerMinute(userId, ACTION, perMinuteLimit);
        } catch (RateLimitExceededException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("global rate limit fail-open — Redis 이슈로 통과: {}", e.getClass().getSimpleName());
        }
    }
}
