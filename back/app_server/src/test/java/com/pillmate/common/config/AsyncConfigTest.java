package com.pillmate.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AsyncConfig — insightTaskExecutor 풀 설정")
class AsyncConfigTest {

    @Test
    @DisplayName("insightTaskExecutor — core=2, max=8, queue=50, prefix='insight-'")
    void insightTaskExecutor_hasExpectedPoolSettings() {
        Executor executor = new AsyncConfig().insightTaskExecutor();

        assertThat(executor).isInstanceOf(ThreadPoolTaskExecutor.class);
        ThreadPoolTaskExecutor pool = (ThreadPoolTaskExecutor) executor;
        assertThat(pool.getCorePoolSize()).isEqualTo(2);
        assertThat(pool.getMaxPoolSize()).isEqualTo(8);
        assertThat(pool.getQueueCapacity()).isEqualTo(50);
        assertThat(pool.getThreadNamePrefix()).isEqualTo("insight-");
    }
}
