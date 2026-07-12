package com.pillmate.common.ratelimit;

public interface RateLimiterPort {

    void checkAndIncrement(long userId, String action, int dailyLimit);

    void checkAndIncrementPerMinute(long userId, String action, int perMinuteLimit);
}
