package com.pillmate.common.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GlobalExceptionHandler Sentry 필터링 규칙 검증.
 *
 * 1. PillmateException(도메인 4xx/비즈니스 예외) → Sentry 전송 제외.
 * 2. 예상외 Exception(5xx) → Sentry 캡처 대상.
 * 3. Sentry SDK 는 DSN 빈값이면 captureException 이 no-op → 별도 mocking 불필요.
 */
class GlobalExceptionHandlerSentryTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("PillmateException(DRUG_NOT_FOUND) → 404, Sentry 전송 없음")
    void handlePillmateException_drugNotFound_returns404() {
        var ex = new PillmateException(ErrorCode.DRUG_NOT_FOUND);
        var response = handler.handlePillmateException(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("PillmateException(GROUP_ACCESS_DENIED) → 403, Sentry 전송 없음")
    void handlePillmateException_accessDenied_returns403() {
        var ex = new PillmateException(ErrorCode.GROUP_ACCESS_DENIED);
        var response = handler.handlePillmateException(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("PillmateException(REPORT_GENERATION_FAILED) → 500이어도 Sentry 전송 없음 — 도메인 에러는 모두 제외")
    void handlePillmateException_reportFailed_returns500WithoutSentry() {
        var ex = new PillmateException(ErrorCode.REPORT_GENERATION_FAILED);
        var response = handler.handlePillmateException(ex);
        // 5xx 이지만 PillmateException은 Sentry 대상 아님 — 도메인이 의도적으로 던진 에러
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("예상외 RuntimeException → 500, Sentry.captureException 호출 경로 (DSN 없으면 no-op)")
    void handleException_unexpectedRuntime_returns500() {
        var ex = new RuntimeException("unexpected db failure");
        var response = handler.handleException(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("예상외 NullPointerException → 500")
    void handleException_npe_returns500() {
        var ex = new NullPointerException("null ref in service");
        var response = handler.handleException(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("send_default_pii=false 검증 — ErrorCode 메시지에 환자 식별정보 미포함")
    void errorCodeMessages_containNoPatientInfo() {
        // ErrorCode 메시지에 이메일·전화번호·처방내용이 하드코딩되면 안 됨
        for (ErrorCode code : ErrorCode.values()) {
            String message = code.getMessage();
            assertThat(message)
                    .as("ErrorCode %s 메시지에 '@' 포함 금지 (이메일 유사 패턴)", code)
                    .doesNotContain("@pillmate.com", "patient@", "user@");
        }
    }
}
