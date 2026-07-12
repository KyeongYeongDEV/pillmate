package com.pillmate.notification.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pillmate.notification.application.port.RecipientCachePort.CachedRecipient;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@Tag("integration")
@Testcontainers
@DisplayName("RedisRecipientCacheAdapter — 그룹 수신자+토큰 캐시")
class RedisRecipientCacheAdapterTest {

    @SuppressWarnings("resource")
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate template;
    private RedisRecipientCacheAdapter adapter;

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        template = new StringRedisTemplate(connectionFactory);
        template.afterPropertiesSet();
        template.getConnectionFactory().getConnection().serverCommands().flushDb();
        adapter = new RedisRecipientCacheAdapter(template, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        connectionFactory.destroy();
    }

    @Test
    @DisplayName("put 후 get — userId+token round-trip (token null 멤버 포함)")
    void putThenGet_roundTrips() {
        adapter.put(10L, List.of(
                new CachedRecipient(1L, "ExponentPushToken[a]"),
                new CachedRecipient(2L, null)));

        Optional<List<CachedRecipient>> cached = adapter.get(10L);

        assertThat(cached).isPresent();
        assertThat(cached.get()).containsExactly(
                new CachedRecipient(1L, "ExponentPushToken[a]"),
                new CachedRecipient(2L, null));
    }

    @Test
    @DisplayName("TTL 5분 이하 설정 (영구 키 금지)")
    void key_hasFiveMinuteTtl() {
        adapter.put(10L, List.of(new CachedRecipient(1L, "tok")));

        Long ttl = template.getExpire("group:10:recipients");
        assertThat(ttl).isPositive();
        assertThat(ttl).isLessThanOrEqualTo(300L);
    }

    @Test
    @DisplayName("evict — 명시 키 DEL, 다음 get 은 miss")
    void evict_removesEntry() {
        adapter.put(10L, List.of(new CachedRecipient(1L, "tok")));

        adapter.evict(10L);

        assertThat(adapter.get(10L)).isEmpty();
        assertThat(template.hasKey("group:10:recipients")).isFalse();
    }

    @Test
    @DisplayName("evict 는 다른 그룹 캐시에 영향 없음")
    void evict_doesNotAffectOtherGroups() {
        adapter.put(10L, List.of(new CachedRecipient(1L, "a")));
        adapter.put(11L, List.of(new CachedRecipient(2L, "b")));

        adapter.evict(10L);

        assertThat(adapter.get(11L)).isPresent();
    }

    @Test
    @DisplayName("Redis 장애 — get/put/evict 전부 graceful (예외 전파 0, DB fallback 경로)")
    void redisDown_gracefulFallback() {
        connectionFactory.destroy();

        assertThatCode(() -> {
            assertThat(adapter.get(10L)).isEmpty();
            adapter.put(10L, List.of(new CachedRecipient(1L, "a")));
            adapter.evict(10L);
        }).doesNotThrowAnyException();
    }
}
