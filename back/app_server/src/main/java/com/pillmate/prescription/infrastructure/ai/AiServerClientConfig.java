package com.pillmate.prescription.infrastructure.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

@Configuration
class AiServerClientConfig {

    @Value("${ai-server.timeout.connect-ms:5000}")
    private int connectTimeout;

    @Value("${ai-server.timeout.read-ms:120000}")
    private int readTimeout;

    @Bean
    public RestClientCustomizer aiServerRestClientCustomizer() {
        return builder -> {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(connectTimeout);
            factory.setReadTimeout(readTimeout);
            builder.requestFactory(factory);
        };
    }
}
