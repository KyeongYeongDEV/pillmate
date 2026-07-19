package com.pillmate.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SentryDsnEnvironmentPostProcessor — 잘못된 DSN으로 인한 앱 크래시 방지")
class SentryDsnEnvironmentPostProcessorTest {

    private final SentryDsnEnvironmentPostProcessor postProcessor = new SentryDsnEnvironmentPostProcessor();

    @Test
    @DisplayName("공개키 없는 잘못된 DSN이면 sentry.dsn을 빈 문자열로 치환한다")
    void postProcessEnvironment_whenDsnInvalid_overridesToEmpty() {
        // given
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("sentry.dsn", "https://o451162.ingest.sentry.io/4507000000000000");

        // when
        postProcessor.postProcessEnvironment(environment, null);

        // then
        assertThat(environment.getProperty("sentry.dsn")).isEmpty();
    }

    @Test
    @DisplayName("정상 DSN이면 값을 그대로 둔다")
    void postProcessEnvironment_whenDsnValid_leavesUnchanged() {
        // given
        String validDsn = "https://abc123publickey@o451162.ingest.sentry.io/4507000000000000";
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("sentry.dsn", validDsn);

        // when
        postProcessor.postProcessEnvironment(environment, null);

        // then
        assertThat(environment.getProperty("sentry.dsn")).isEqualTo(validDsn);
    }

    @Test
    @DisplayName("빈 DSN이면 그대로 빈 값으로 둔다 (로컬 개발 환경 Sentry OFF)")
    void postProcessEnvironment_whenDsnBlank_leavesEmpty() {
        // given
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("sentry.dsn", "");

        // when
        postProcessor.postProcessEnvironment(environment, null);

        // then
        assertThat(environment.getProperty("sentry.dsn")).isEmpty();
    }
}
