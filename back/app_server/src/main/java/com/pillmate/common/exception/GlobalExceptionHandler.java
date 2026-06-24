package com.pillmate.common.exception;

import com.pillmate.common.response.ApiResponse;
import io.sentry.Sentry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 도메인 비즈니스 예외 — 예상된 흐름이므로 Sentry 전송 제외.
     */
    @ExceptionHandler(PillmateException.class)
    public ResponseEntity<ApiResponse<Void>> handlePillmateException(PillmateException e) {
        HttpStatus status = resolveStatus(e.getErrorCode());
        return ResponseEntity.status(status)
                .body(ApiResponse.error(e.getErrorCode()));
    }

    /**
     * 입력값 검증 실패 — 클라이언트 문제이므로 Sentry 전송 제외.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.INVALID_REQUEST));
    }

    /**
     * Redis 연결 실패 — 인프라 이상이므로 Sentry 캡처.
     * 환자정보·처방내용은 로그/이벤트에 포함하지 않음.
     */
    @ExceptionHandler(RedisConnectionFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleRedisDown(RedisConnectionFailureException e) {
        log.error("Redis connection failure: {}", e.getMessage());
        Sentry.captureException(e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(ErrorCode.INVITE_CACHE_UNAVAILABLE));
    }

    /**
     * 예상외 예외 — 5xx, Sentry 캡처.
     * ★ 메시지에 환자정보·처방내용 포함 금지 (medical-safety 룰).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("Unhandled exception {}: {}", e.getClass().getName(), e.getMessage(), e);
        Sentry.captureException(e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    private HttpStatus resolveStatus(ErrorCode code) {
        return switch (code) {
            case DRUG_NOT_FOUND, GROUP_NOT_FOUND, PRESCRIPTION_NOT_FOUND, SCHEDULE_NOT_FOUND,
                 GROUP_INVITE_CODE_INVALID, REPORT_NOT_FOUND, ITEM_SEQ_NOT_FOUND, ALIAS_NOT_FOUND,
                 NOTIFICATION_NOT_FOUND, INVALID_NOTIFICATION_DOSE_LOG -> HttpStatus.NOT_FOUND;
            case GROUP_ACCESS_DENIED, NOT_NOTIFICATION_OWNER, PATIENT_ACCESS_DENIED -> HttpStatus.FORBIDDEN;
            case GROUP_INVITE_CODE_EXPIRED, INVITE_CODE_EXPIRED_OR_INVALID -> HttpStatus.GONE;
            case GROUP_INVITE_CODE_USED, GROUP_ALREADY_MEMBER, SCHEDULE_CONFLICT,
                 SCHEDULE_PERIOD_ENDED, DOSE_LOG_DATE_LOCKED -> HttpStatus.CONFLICT;
            case DRUG_SEARCH_EMPTY_QUERY, INVALID_REQUEST,
                 PRESCRIPTION_DRUG_NOT_MATCHED, PRESCRIPTION_ITEMS_EMPTY,
                 SCHEDULE_INVALID_TIME_OF_DAY, SCHEDULE_INVALID_PERIOD,
                 OCR_REQUEST_INVALID -> HttpStatus.BAD_REQUEST;
            case OCR_UPSTREAM_TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case OCR_UPSTREAM_FAILED -> HttpStatus.BAD_GATEWAY;
            case OCR_EMPTY -> HttpStatus.UNPROCESSABLE_ENTITY;
            case REPORT_REFRESH_RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case REPORT_GENERATION_FAILED -> HttpStatus.INTERNAL_SERVER_ERROR;
            case INVITE_CACHE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case KAKAO_AUTH_FAILED, INVALID_AUTH_TOKEN -> HttpStatus.UNAUTHORIZED;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
