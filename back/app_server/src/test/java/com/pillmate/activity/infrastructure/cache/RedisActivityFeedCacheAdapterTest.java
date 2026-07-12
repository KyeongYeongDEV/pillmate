package com.pillmate.activity.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pillmate.activity.application.dto.ActivityFeedItem;
import com.pillmate.activity.domain.model.ActivitySeverity;
import com.pillmate.activity.domain.model.ActivityType;
import com.pillmate.schedule.domain.model.TimeOfDay;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@Tag("integration")
@Testcontainers
@DisplayName("RedisActivityFeedCacheAdapter — 그룹 피드 캐시 (버전 키 무효화)")
class RedisActivityFeedCacheAdapterTest {

    @SuppressWarnings("resource")
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate template;
    private RedisActivityFeedCacheAdapter adapter;

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        template = new StringRedisTemplate(connectionFactory);
        template.afterPropertiesSet();
        template.getConnectionFactory().getConnection().serverCommands().flushDb();
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        adapter = new RedisActivityFeedCacheAdapter(template, objectMapper);
    }

    @AfterEach
    void tearDown() {
        connectionFactory.destroy();
    }

    private List<ActivityFeedItem> items(String summary) {
        return List.of(new ActivityFeedItem(
                "할머니", ActivityType.DOSE_TAKEN, TimeOfDay.NOON, summary,
                ActivitySeverity.INFO, Instant.parse("2026-07-02T03:00:00Z")));
    }

    @Test
    @DisplayName("put 후 get — 동일 (group, viewer, limit) 키에서 round-trip")
    void putThenGet_roundTrips() {
        adapter.putGroupFeed(10L, 1L, 20, items("점심약 복용"));

        Optional<List<ActivityFeedItem>> cached = adapter.getGroupFeed(10L, 1L, 20);

        assertThat(cached).isPresent();
        assertThat(cached.get()).hasSize(1);
        assertThat(cached.get().get(0).summary()).isEqualTo("점심약 복용");
        assertThat(cached.get().get(0).occurredAt()).isEqualTo(Instant.parse("2026-07-02T03:00:00Z"));
    }

    @Test
    @DisplayName("viewer 가 다르면 캐시 분리 (viewer 제외 시맨틱 누출 방지)")
    void differentViewer_isolatedEntries() {
        adapter.putGroupFeed(10L, 1L, 20, items("viewer1 피드"));

        assertThat(adapter.getGroupFeed(10L, 2L, 20)).isEmpty();
    }

    @Test
    @DisplayName("데이터 키 TTL 15초 이하 설정 (영구 키 금지)")
    void dataKey_hasTtl() {
        adapter.putGroupFeed(10L, 1L, 20, items("점심약 복용"));

        String dataKey = template.keys("feed:group:10:v0:1:20").iterator().next();
        Long ttl = template.getExpire(dataKey);
        assertThat(ttl).isPositive();
        assertThat(ttl).isLessThanOrEqualTo(15L);
    }

    @Test
    @DisplayName("evictGroup — 버전 INCR 1회로 그룹 전체 캐시 무효 (viewer/limit 열거·와일드카드 없이)")
    void evictGroup_invalidatesAllViewersAndLimits() {
        adapter.putGroupFeed(10L, 1L, 20, items("v1"));
        adapter.putGroupFeed(10L, 2L, 10, items("v2"));

        adapter.evictGroup(10L);

        assertThat(adapter.getGroupFeed(10L, 1L, 20)).isEmpty();
        assertThat(adapter.getGroupFeed(10L, 2L, 10)).isEmpty();
    }

    @Test
    @DisplayName("evictGroup 은 다른 그룹 캐시에 영향 없음")
    void evictGroup_doesNotAffectOtherGroups() {
        adapter.putGroupFeed(10L, 1L, 20, items("group10"));
        adapter.putGroupFeed(11L, 1L, 20, items("group11"));

        adapter.evictGroup(10L);

        assertThat(adapter.getGroupFeed(10L, 1L, 20)).isEmpty();
        assertThat(adapter.getGroupFeed(11L, 1L, 20)).isPresent();
    }

    @Test
    @DisplayName("버전 키에도 TTL 존재 (영구 키 금지)")
    void versionKey_hasTtl() {
        adapter.evictGroup(10L);

        Long ttl = template.getExpire("feed:group:10:ver");
        assertThat(ttl).isPositive();
    }

    @Test
    @DisplayName("Redis 장애 (connection 종료) — get/put/evict 전부 graceful (예외 전파 0, DB fallback 경로)")
    void redisDown_gracefulFallback() {
        connectionFactory.destroy();

        assertThatCode(() -> {
            assertThat(adapter.getGroupFeed(10L, 1L, 20)).isEmpty();
            adapter.putGroupFeed(10L, 1L, 20, items("x"));
            adapter.evictGroup(10L);
        }).doesNotThrowAnyException();
    }
}
