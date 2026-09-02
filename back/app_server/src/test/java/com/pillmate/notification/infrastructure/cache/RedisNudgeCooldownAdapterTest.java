package com.pillmate.notification.infrastructure.cache;

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

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Tag("integration")
@Testcontainers
@DisplayName("RedisNudgeCooldownAdapter — SETNX 쿨다운")
class RedisNudgeCooldownAdapterTest {

    @SuppressWarnings("resource")
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactory;
    private RedisNudgeCooldownAdapter adapter;

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
        template.afterPropertiesSet();
        template.getConnectionFactory().getConnection().serverCommands().flushDb();
        adapter = new RedisNudgeCooldownAdapter(template);
    }

    @AfterEach
    void tearDown() {
        connectionFactory.destroy();
    }

    @Test
    @DisplayName("최초 요청 — 획득 성공(true)")
    void tryAcquire_firstRequest_succeeds() {
        boolean acquired = adapter.tryAcquire(5L, 2L, Duration.ofMinutes(10));

        assertThat(acquired).isTrue();
    }

    @Test
    @DisplayName("동일 doseLogId+fromUserId 연타 — 쿨다운 중이면 실패(false)")
    void tryAcquire_sameKeyWithinCooldown_fails() {
        adapter.tryAcquire(5L, 2L, Duration.ofMinutes(10));

        boolean secondAttempt = adapter.tryAcquire(5L, 2L, Duration.ofMinutes(10));

        assertThat(secondAttempt).isFalse();
    }

    @Test
    @DisplayName("다른 fromUserId — 동시에 각각 획득 성공 (사용자별 독립 쿨다운)")
    void tryAcquire_differentFromUser_independentCooldown() {
        adapter.tryAcquire(5L, 2L, Duration.ofMinutes(10));

        boolean otherUser = adapter.tryAcquire(5L, 3L, Duration.ofMinutes(10));

        assertThat(otherUser).isTrue();
    }

    @Test
    @DisplayName("당사자 총량 캡 — 최초 획득 성공(true)")
    void acquireRecipientCap_firstRequest_succeeds() {
        boolean acquired = adapter.acquireRecipientCap(1L, Duration.ofMinutes(10));

        assertThat(acquired).isTrue();
    }

    @Test
    @DisplayName("당사자 총량 캡 — 이미 다른 발신자/dose 로 획득된 상태면 실패(false)")
    void acquireRecipientCap_alreadyAcquiredByAnotherSender_fails() {
        adapter.acquireRecipientCap(1L, Duration.ofMinutes(10));

        boolean second = adapter.acquireRecipientCap(1L, Duration.ofMinutes(10));

        assertThat(second).isFalse();
    }

    @Test
    @DisplayName("당사자 총량 캡 — TTL 만료 후에는 다시 획득 성공(재발송 가능)")
    void acquireRecipientCap_afterTtlExpiry_succeedsAgain() {
        adapter.acquireRecipientCap(1L, Duration.ofMillis(200));

        await().atMost(Duration.ofSeconds(3))
                .until(() -> adapter.acquireRecipientCap(1L, Duration.ofMinutes(10)));
    }
}
