package com.pillmate.common.security;

import com.pillmate.user.application.UserActivityRecordingService;
import com.pillmate.user.presentation.interceptor.ActivityRecordingInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired(required = false)
    private JwtTokenProvider jwtTokenProvider;

    @Autowired(required = false)
    private UserActivityRecordingService userActivityRecordingService;

    @Value("${pillmate.auth.dev-fallback-enabled:false}")
    private boolean devFallbackEnabled;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // UserContextInterceptor 먼저 등록 → afterCompletion은 역순이므로
        // ActivityRecordingInterceptor.afterCompletion이 먼저 실행되어 UserContext가 유효함
        registry.addInterceptor(new UserContextInterceptor(jwtTokenProvider, devFallbackEnabled))
                .addPathPatterns("/**")
                .excludePathPatterns("/auth/**", "/v3/api-docs/**", "/swagger-ui/**");

        if (userActivityRecordingService != null) {
            registry.addInterceptor(new ActivityRecordingInterceptor(userActivityRecordingService))
                    .addPathPatterns("/**")
                    .excludePathPatterns("/auth/**", "/actuator/**", "/v3/api-docs/**", "/swagger-ui/**");
        }
    }
}
