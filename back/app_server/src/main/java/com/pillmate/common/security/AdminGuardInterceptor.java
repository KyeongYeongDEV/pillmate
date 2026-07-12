package com.pillmate.common.security;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * 관리 엔드포인트(약품 alias 승인 등)를 env 허용 목록 사용자만 접근하도록 제한.
 * 정식 RBAC 대신 no-overengineering 수준의 userId 허용 목록. 목록이 비면 fail-closed(전원 차단).
 */
@RequiredArgsConstructor
public class AdminGuardInterceptor implements HandlerInterceptor {

    private final Set<Long> adminUserIds;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Long userId = UserContext.get();
        if (userId == null || !adminUserIds.contains(userId)) {
            throw new PillmateException(ErrorCode.ADMIN_ACCESS_DENIED);
        }
        return true;
    }
}
