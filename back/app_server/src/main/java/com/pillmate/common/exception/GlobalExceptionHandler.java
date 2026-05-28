package com.pillmate.common.exception;

import com.pillmate.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PillmateException.class)
    public ResponseEntity<ApiResponse<Void>> handlePillmateException(PillmateException e) {
        HttpStatus status = resolveStatus(e.getErrorCode());
        return ResponseEntity.status(status)
                .body(ApiResponse.error(e.getErrorCode()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.INVALID_REQUEST));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("Unhandled exception {}: {}", e.getClass().getName(), e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    private HttpStatus resolveStatus(ErrorCode code) {
        return switch (code) {
            case DRUG_NOT_FOUND, GROUP_NOT_FOUND, PRESCRIPTION_NOT_FOUND, SCHEDULE_NOT_FOUND,
                 GROUP_INVITE_CODE_INVALID, REPORT_NOT_FOUND, ITEM_SEQ_NOT_FOUND, ALIAS_NOT_FOUND,
                 NOTIFICATION_NOT_FOUND, INVALID_NOTIFICATION_DOSE_LOG -> HttpStatus.NOT_FOUND;
            case GROUP_ACCESS_DENIED, NOT_NOTIFICATION_OWNER, PATIENT_ACCESS_DENIED -> HttpStatus.FORBIDDEN;
            case GROUP_INVITE_CODE_EXPIRED -> HttpStatus.GONE;
            case GROUP_INVITE_CODE_USED, GROUP_ALREADY_MEMBER, SCHEDULE_CONFLICT -> HttpStatus.CONFLICT;
            case DRUG_SEARCH_EMPTY_QUERY, INVALID_REQUEST,
                 PRESCRIPTION_DRUG_NOT_MATCHED, PRESCRIPTION_ITEMS_EMPTY,
                 OCR_REQUEST_INVALID -> HttpStatus.BAD_REQUEST;
            case OCR_UPSTREAM_TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case OCR_UPSTREAM_FAILED -> HttpStatus.BAD_GATEWAY;
            case OCR_EMPTY -> HttpStatus.UNPROCESSABLE_ENTITY;
            case REPORT_REFRESH_RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case REPORT_GENERATION_FAILED -> HttpStatus.INTERNAL_SERVER_ERROR;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
