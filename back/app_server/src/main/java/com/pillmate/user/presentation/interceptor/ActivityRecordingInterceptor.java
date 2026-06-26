package com.pillmate.user.presentation.interceptor;

import com.pillmate.common.security.UserContext;
import com.pillmate.user.application.UserActivityRecordingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;

// presentation layer: primary adapter — HTTP 요청을 application 서비스로 전달
// @Component 제거: WebMvcConfig에서 직접 생성 → @WebMvcTest 컨텍스트에서 자동 로드되지 않음
@RequiredArgsConstructor
@Slf4j
public class ActivityRecordingInterceptor implements HandlerInterceptor {

    private final UserActivityRecordingService userActivityRecordingService;

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        Long userId = UserContext.get();
        if (userId == null) {
            return;
        }
        try {
            userActivityRecordingService.record(userId);
        } catch (Exception e) {
            log.warn("ActivityRecordingInterceptor: 활동 기록 중 예외 (무시)", e);
        }
    }
}
