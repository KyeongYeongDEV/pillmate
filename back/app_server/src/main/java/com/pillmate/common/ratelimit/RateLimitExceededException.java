package com.pillmate.common.ratelimit;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;

public class RateLimitExceededException extends PillmateException {

    public RateLimitExceededException() {
        super(ErrorCode.RATE_LIMIT_EXCEEDED);
    }
}
