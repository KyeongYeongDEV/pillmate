package com.pillmate.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Optional;

/**
 * SentryAutoConfiguration이 뜨기 전에 sentry.dsn 형식을 검증한다.
 * 형식이 잘못된 DSN이 주입되면 SentryAutoConfiguration이 앱 기동 자체를 실패시키므로
 * (프로드 크래시 루프 이력), 무효 DSN은 빈 값으로 치환해 Sentry만 비활성화한다.
 */
public class SentryDsnEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final Logger log = LoggerFactory.getLogger(SentryDsnEnvironmentPostProcessor.class);
    private static final String DSN_PROPERTY = "sentry.dsn";
    private static final String GUARD_PROPERTY_SOURCE_NAME = "pillmateSentryDsnGuard";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String dsn = environment.getProperty(DSN_PROPERTY);
        if (!StringUtils.hasText(dsn)) {
            return;
        }

        Optional<String> invalidReason = SentryDsnValidator.invalidReason(dsn);
        if (invalidReason.isEmpty()) {
            return;
        }

        log.warn("Sentry DSN 형식 오류 — 비활성화 (원인: {})", invalidReason.get());
        environment.getPropertySources().addFirst(disabledDsnPropertySource());
    }

    private MapPropertySource disabledDsnPropertySource() {
        return new MapPropertySource(GUARD_PROPERTY_SOURCE_NAME, Collections.singletonMap(DSN_PROPERTY, ""));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
