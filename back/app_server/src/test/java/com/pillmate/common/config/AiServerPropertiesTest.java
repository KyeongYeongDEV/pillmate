package com.pillmate.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AiServerProperties — ai-server.* 바인딩")
class AiServerPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    @DisplayName("ai-server.base-url / timeout.connect-ms / timeout.read-ms 를 record 로 바인딩한다")
    void binds_aiServerProperties_fromYamlKeys() {
        contextRunner
                .withPropertyValues(
                        "ai-server.base-url=http://ai-server:8001",
                        "ai-server.timeout.connect-ms=5000",
                        "ai-server.timeout.read-ms=170000")
                .run(context -> {
                    AiServerProperties properties = context.getBean(AiServerProperties.class);
                    assertThat(properties.baseUrl()).isEqualTo("http://ai-server:8001");
                    assertThat(properties.timeout().connectMs()).isEqualTo(5000);
                    assertThat(properties.timeout().readMs()).isEqualTo(170000);
                });
    }

    @Configuration
    @EnableConfigurationProperties(AiServerProperties.class)
    static class TestConfig {
    }
}
