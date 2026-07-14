package com.pillmate.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ai-server.* — prescription(OCR/추천)·report(인사이트) 두 컨텍스트가 공유하는
 * AI 서버 연결 설정. 컨텍스트 간 직접 import 를 피하기 위해 common 에 배치.
 */
@ConfigurationProperties(prefix = "ai-server")
public record AiServerProperties(String baseUrl, Timeout timeout) {

    public record Timeout(int connectMs, int readMs) {
    }
}
