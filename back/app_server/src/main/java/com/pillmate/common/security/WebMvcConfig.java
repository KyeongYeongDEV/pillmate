package com.pillmate.common.security;

import com.pillmate.common.ratelimit.GlobalRateLimitInterceptor;
import com.pillmate.common.ratelimit.RateLimiterPort;
import com.pillmate.user.application.UserActivityRecordingService;
import com.pillmate.user.presentation.interceptor.ActivityRecordingInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired(required = false)
    private JwtTokenProvider jwtTokenProvider;

    @Autowired(required = false)
    private UserActivityRecordingService userActivityRecordingService;

    @Autowired(required = false)
    private RateLimiterPort rateLimiterPort;

    @Value("${pillmate.auth.dev-fallback-enabled:false}")
    private boolean devFallbackEnabled;

    @Value("${pillmate.ratelimit.enabled:true}")
    private boolean rateLimitEnabled;

    @Value("${pillmate.ratelimit.global.per-minute:120}")
    private int globalPerMinute;

    @Value("${pillmate.admin.user-ids:}")
    private String adminUserIds;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // UserContextInterceptor 먼저 등록 → afterCompletion은 역순이므로
        // ActivityRecordingInterceptor.afterCompletion이 먼저 실행되어 UserContext가 유효함
        registry.addInterceptor(new UserContextInterceptor(jwtTokenProvider, devFallbackEnabled))
                .addPathPatterns("/**")
                .excludePathPatterns("/auth/**", "/v3/api-docs/**", "/swagger-ui/**");

        // 관리 엔드포인트 접근통제 — UserContext 설정 이후 등록. 허용 목록 비면 전원 차단(fail-closed)
        registry.addInterceptor(new AdminGuardInterceptor(parseAdminUserIds()))
                .addPathPatterns("/drugs/aliases/pending-review", "/drugs/aliases/*/verify");

        // 전역 per-user throttle — UserContext 설정 이후에 등록해야 userId 를 읽는다 (계층 ②)
        if (rateLimiterPort != null) {
            registry.addInterceptor(new GlobalRateLimitInterceptor(rateLimiterPort, rateLimitEnabled, globalPerMinute))
                    .addPathPatterns("/**")
                    .excludePathPatterns("/auth/**", "/actuator/**", "/v3/api-docs/**", "/swagger-ui/**");
        }

        if (userActivityRecordingService != null) {
            registry.addInterceptor(new ActivityRecordingInterceptor(userActivityRecordingService))
                    .addPathPatterns("/**")
                    .excludePathPatterns("/auth/**", "/actuator/**", "/v3/api-docs/**", "/swagger-ui/**");
        }
    }

    private Set<Long> parseAdminUserIds() {
        if (adminUserIds == null || adminUserIds.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(adminUserIds.split(","))
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .map(Long::parseLong)
                .collect(Collectors.toUnmodifiableSet());
    }
}
