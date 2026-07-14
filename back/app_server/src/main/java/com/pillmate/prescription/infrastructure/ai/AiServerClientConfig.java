package com.pillmate.prescription.infrastructure.ai;

import com.pillmate.common.config.AiServerProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

@Configuration
@RequiredArgsConstructor
class AiServerClientConfig {

    private final AiServerProperties properties;

    @Bean
    public RestClientCustomizer aiServerRestClientCustomizer() {
        return builder -> {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(properties.timeout().connectMs());
            factory.setReadTimeout(properties.timeout().readMs());
            builder.requestFactory(factory);
        };
    }
}
