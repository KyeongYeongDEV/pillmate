package com.pillmate.common.security;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.time.Instant;

public class UserContextInterceptor implements HandlerInterceptor {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final boolean devFallbackEnabled;

    public UserContextInterceptor(JwtTokenProvider jwtTokenProvider, boolean devFallbackEnabled) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.devFallbackEnabled = devFallbackEnabled;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith(BEARER_PREFIX) && jwtTokenProvider != null) {
            return handleBearer(auth.substring(BEARER_PREFIX.length()), response);
        }
        if (devFallbackEnabled) {
            applyXUserIdFallback(request);
            return true;
        }
        sendUnauthorized(response, ErrorCode.INVALID_AUTH_TOKEN);
        return false;
    }

    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse res, Object handler, Exception ex) {
        UserContext.clear();
    }

    private boolean handleBearer(String token, HttpServletResponse response) throws IOException {
        try {
            UserContext.set(jwtTokenProvider.parseUserId(token));
            return true;
        } catch (PillmateException e) {
            sendUnauthorized(response, e.getErrorCode());
            return false;
        }
    }

    private void applyXUserIdFallback(HttpServletRequest request) {
        String userId = request.getHeader(USER_ID_HEADER);
        if (userId == null) return;
        try {
            UserContext.set(Long.parseLong(userId));
        } catch (NumberFormatException ignored) {
        }
    }

    private void sendUnauthorized(HttpServletResponse response, ErrorCode code) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"error\":{\"code\":\"" + code.getCode() + "\",\"message\":\"" + code.getMessage() + "\"},\"timestamp\":\"" + Instant.now() + "\"}");
    }
}
