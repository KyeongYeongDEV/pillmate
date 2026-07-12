package com.pillmate.common.security;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AdminGuardInterceptor — 관리 엔드포인트 허용 목록 접근통제")
class AdminGuardInterceptorTest {

    private final HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
    private final HttpServletResponse response = Mockito.mock(HttpServletResponse.class);

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("허용 목록 사용자 → 통과")
    void preHandle_adminUser_passes() {
        AdminGuardInterceptor sut = new AdminGuardInterceptor(Set.of(1L, 7L));
        UserContext.set(7L);

        boolean result = sut.preHandle(request, response, new Object());

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("비허용 사용자 → ADMIN_ACCESS_DENIED(403)")
    void preHandle_nonAdminUser_throwsForbidden() {
        AdminGuardInterceptor sut = new AdminGuardInterceptor(Set.of(1L));
        UserContext.set(99L);

        assertThatThrownBy(() -> sut.preHandle(request, response, new Object()))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ADMIN_ACCESS_DENIED);
    }

    @Test
    @DisplayName("인증 없음(UserContext null) → ADMIN_ACCESS_DENIED(403)")
    void preHandle_noUser_throwsForbidden() {
        AdminGuardInterceptor sut = new AdminGuardInterceptor(Set.of(1L));

        assertThatThrownBy(() -> sut.preHandle(request, response, new Object()))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ADMIN_ACCESS_DENIED);
    }

    @Test
    @DisplayName("허용 목록 비어 있으면 fail-closed — 어떤 사용자도 차단")
    void preHandle_emptyAllowList_deniesEveryone() {
        AdminGuardInterceptor sut = new AdminGuardInterceptor(Set.of());
        UserContext.set(1L);

        assertThatThrownBy(() -> sut.preHandle(request, response, new Object()))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ADMIN_ACCESS_DENIED);
    }

    @Test
    @DisplayName("허용 목록 사용자면 예외 없이 통과 (assertThatCode)")
    void preHandle_adminUser_doesNotThrow() {
        AdminGuardInterceptor sut = new AdminGuardInterceptor(Set.of(1L));
        UserContext.set(1L);

        assertThatCode(() -> sut.preHandle(request, response, new Object())).doesNotThrowAnyException();
    }
}
