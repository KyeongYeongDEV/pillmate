package com.pillmate.user.presentation.interceptor;

import com.pillmate.common.security.UserContext;
import com.pillmate.user.application.UserActivityRecordingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ActivityRecordingInterceptor — 활동 기록 인터셉터")
class ActivityRecordingInterceptorTest {

    @Mock UserActivityRecordingService userActivityRecordingService;

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    @DisplayName("인증된 요청(UserContext 설정됨) → record() 호출")
    void afterCompletion_authenticatedRequest_recordsCalled() throws Exception {
        UserContext.set(99L);
        ActivityRecordingInterceptor interceptor = new ActivityRecordingInterceptor(userActivityRecordingService);

        interceptor.afterCompletion(
                new MockHttpServletRequest(), new MockHttpServletResponse(), new Object(), null);

        verify(userActivityRecordingService).record(99L);
    }

    @Test
    @DisplayName("비인증 요청(UserContext null) → record() 호출 안 함")
    void afterCompletion_unauthenticatedRequest_recordsNotCalled() throws Exception {
        ActivityRecordingInterceptor interceptor = new ActivityRecordingInterceptor(userActivityRecordingService);

        interceptor.afterCompletion(
                new MockHttpServletRequest(), new MockHttpServletResponse(), new Object(), null);

        verify(userActivityRecordingService, never()).record(org.mockito.ArgumentMatchers.anyLong());
    }
}
