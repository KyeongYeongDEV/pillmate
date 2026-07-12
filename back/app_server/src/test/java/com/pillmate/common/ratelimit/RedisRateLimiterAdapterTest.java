package com.pillmate.common.ratelimit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
@Testcontainers
@DisplayName("RedisRateLimiterAdapter — INCR + 자정 TTL 일일 한도")
class RedisRateLimiterAdapterTest {

    // KST 2026-07-02 12:00 — 자정까지 12시간
    private static final Instant FIXED_NOW = Instant.parse("2026-07-02T03:00:00Z");
    private static final Duration UNTIL_KST_MIDNIGHT = Duration.ofHours(12);
    private static final long USER_ID = 7L;

    @SuppressWarnings("resource")
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate template;
    private RedisRateLimiterAdapter adapter;

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        template = new StringRedisTemplate(connectionFactory);
        template.afterPropertiesSet();
        template.getConnectionFactory().getConnection().serverCommands().flushDb();
        adapter = new RedisRateLimiterAdapter(template, Clock.fixed(FIXED_NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        connectionFactory.destroy();
    }

    @Test
    @DisplayName("첫 호출 — 카운트 1 + KST 자정까지 TTL 설정 (영구 키 금지)")
    void firstCall_setsCountOneAndMidnightTtl() {
        adapter.checkAndIncrement(USER_ID, "ocr", 50);

        String key = "rl:ocr:7:20260702";
        assertThat(template.opsForValue().get(key)).isEqualTo("1");
        Long ttl = template.getExpire(key);
        assertThat(ttl).isPositive();
        assertThat(ttl).isLessThanOrEqualTo(UNTIL_KST_MIDNIGHT.toSeconds());
        assertThat(ttl).isGreaterThan(UNTIL_KST_MIDNIGHT.minusMinutes(1).toSeconds());
    }

    @Test
    @DisplayName("한도 내 반복 호출 — 예외 없이 카운트 증가")
    void withinLimit_incrementsWithoutException() {
        assertThatCode(() -> {
            for (int i = 0; i < 50; i++) {
                adapter.checkAndIncrement(USER_ID, "ocr", 50);
            }
        }).doesNotThrowAnyException();

        assertThat(template.opsForValue().get("rl:ocr:7:20260702")).isEqualTo("50");
    }

    @Test
    @DisplayName("한도+1 회 호출 — RateLimitExceededException (429 매핑 대상)")
    void exceedingLimit_throws() {
        for (int i = 0; i < 3; i++) {
            adapter.checkAndIncrement(USER_ID, "ocr", 3);
        }

        assertThatThrownBy(() -> adapter.checkAndIncrement(USER_ID, "ocr", 3))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    @DisplayName("사용자별 격리 — 한 사용자 초과가 다른 사용자에 영향 없음")
    void perUserIsolation() {
        for (int i = 0; i < 4; i++) {
            try {
                adapter.checkAndIncrement(USER_ID, "ocr", 3);
            } catch (RateLimitExceededException ignored) {
            }
        }

        assertThatCode(() -> adapter.checkAndIncrement(99L, "ocr", 3))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("키 날짜는 KST 기준 (UTC 03:00 = KST 12:00 → 20260702)")
    void keyDate_usesKst() {
        adapter.checkAndIncrement(USER_ID, "ocr", 50);

        assertThat(template.hasKey("rl:ocr:7:20260702")).isTrue();
    }

    // ─── T-BE-GLOBAL-RATE-LIMIT: 분당 버킷 (전역 per-user) ───────────────────

    @Test
    @DisplayName("분당 버킷 — 첫 호출 count=1 + TTL 60초 이하 (분 키, KST HHmm)")
    void perMinute_firstCall_setsCountOneAnd60sTtl() {
        adapter.checkAndIncrementPerMinute(USER_ID, "req", 120);

        // KST 2026-07-02 12:00 → 202607021200
        String key = "rl:req:7:202607021200";
        assertThat(template.opsForValue().get(key)).isEqualTo("1");
        Long ttl = template.getExpire(key);
        assertThat(ttl).isPositive();
        assertThat(ttl).isLessThanOrEqualTo(60L);
    }

    @Test
    @DisplayName("분당 한도+1 회 — RateLimitExceededException")
    void perMinute_exceeding_throws() {
        for (int i = 0; i < 3; i++) {
            adapter.checkAndIncrementPerMinute(USER_ID, "req", 3);
        }

        assertThatThrownBy(() -> adapter.checkAndIncrementPerMinute(USER_ID, "req", 3))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    @DisplayName("분당 버킷 — 한도 내 반복은 예외 없음")
    void perMinute_withinLimit_noException() {
        assertThatCode(() -> {
            for (int i = 0; i < 120; i++) {
                adapter.checkAndIncrementPerMinute(USER_ID, "req", 120);
            }
        }).doesNotThrowAnyException();
    }
}
