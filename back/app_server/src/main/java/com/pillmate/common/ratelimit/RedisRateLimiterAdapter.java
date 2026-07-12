package com.pillmate.common.ratelimit;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
public class RedisRateLimiterAdapter implements RateLimiterPort {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter MINUTE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final String KEY_PREFIX = "rl";
    private static final Duration MINUTE_BUCKET_TTL = Duration.ofSeconds(60);

    private final StringRedisTemplate redisTemplate;
    private final Clock clock;

    @Override
    public void checkAndIncrement(long userId, String action, int dailyLimit) {
        String key = buildDailyKey(userId, action);
        incrementAndEnforce(key, dailyLimit, this::untilKstMidnight);
    }

    @Override
    public void checkAndIncrementPerMinute(long userId, String action, int perMinuteLimit) {
        String key = buildMinuteKey(userId, action);
        incrementAndEnforce(key, perMinuteLimit, () -> MINUTE_BUCKET_TTL);
    }

    private void incrementAndEnforce(String key, int limit, java.util.function.Supplier<Duration> ttl) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count == null) {
            return;
        }
        if (count == 1L) {
            redisTemplate.expire(key, ttl.get());
        }
        if (count > limit) {
            throw new RateLimitExceededException();
        }
    }

    private String buildDailyKey(long userId, String action) {
        String day = LocalDate.now(clock.withZone(KST)).format(DAY_FORMAT);
        return KEY_PREFIX + ":" + action + ":" + userId + ":" + day;
    }

    private String buildMinuteKey(long userId, String action) {
        String minute = ZonedDateTime.now(clock.withZone(KST)).format(MINUTE_FORMAT);
        return KEY_PREFIX + ":" + action + ":" + userId + ":" + minute;
    }

    private Duration untilKstMidnight() {
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(KST));
        ZonedDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay(KST);
        return Duration.between(now, midnight);
    }
}
