package com.pillmate.common.response;

import com.pillmate.common.exception.ErrorCode;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class ApiResponse<T> {

    private final T data;
    private final String message;
    private final String timestamp;
    private final ErrorInfo error;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .data(data)
                .message("success")
                .timestamp(Instant.now().toString())
                .build();
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode) {
        return ApiResponse.<T>builder()
                .error(new ErrorInfo(errorCode.getCode(), errorCode.getMessage()))
                .timestamp(Instant.now().toString())
                .build();
    }

    public record ErrorInfo(String code, String message) {}
}
