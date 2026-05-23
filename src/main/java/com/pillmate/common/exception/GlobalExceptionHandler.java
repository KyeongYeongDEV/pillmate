package com.pillmate.common.exception;

import com.pillmate.common.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    private HttpStatus resolveStatus(ErrorCode code) {
        return switch (code) {
            case DRUG_NOT_FOUND, GROUP_NOT_FOUND, PRESCRIPTION_NOT_FOUND, SCHEDULE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case GROUP_ACCESS_DENIED -> HttpStatus.FORBIDDEN;
            case DRUG_SEARCH_EMPTY_QUERY, SCHEDULE_CONFLICT, INVALID_REQUEST,
                 PRESCRIPTION_DRUG_NOT_MATCHED, PRESCRIPTION_ITEMS_EMPTY -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
