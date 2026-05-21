package com.pillmate.common.exception;

import lombok.Getter;

@Getter
public class PillmateException extends RuntimeException {

    private final ErrorCode errorCode;

    public PillmateException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
