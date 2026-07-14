package com.pillmate.report.infrastructure.ai;

import com.pillmate.common.config.AiServerProperties;
import com.pillmate.report.application.port.LlmInsightPort;
import com.pillmate.report.application.port.PrescriptionContextPort.DrugSummary;
import com.pillmate.report.domain.model.InsightSeverity;
import com.pillmate.report.domain.model.InsightType;
import com.pillmate.report.domain.service.DetectedPattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
class AiServerInsightClient implements LlmInsightPort {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final String ENDPOINT = "/api/v1/analyze/health-report";

    private final RestClient restClient;

    AiServerInsightClient(AiServerProperties properties) {
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
                    setConnectTimeout((int) TIMEOUT.toMillis());
                    setReadTimeout((int) TIMEOUT.toMillis());
                }})
                .build();
    }

    @Override
    public List<InsightDraft> generate(InsightContext context) {
        try {
            AiResponse response = restClient.post()
                    .uri(ENDPOINT)
                    .body(toRequest(context))
                    .retrieve()
                    .body(AiResponse.class);
            return response == null ? List.of() : response.toDrafts();
        } catch (RestClientException ex) {
            log.warn("ai-server insight call failed reason={}", ex.getClass().getSimpleName());
            return List.of();
        }
    }

    private Map<String, Object> toRequest(InsightContext c) {
        return Map.of(
                "patientId", c.patientId(),
                "periodType", c.periodType().name(),
                "score", c.score(),
                "adherence_rate", c.adherenceRate(),
                "patterns", c.patterns().stream().map(this::toPatternMap).toList(),
                "drugs", c.drugs().stream().map(this::toDrugMap).toList());
    }

    private Map<String, Object> toPatternMap(DetectedPattern p) {
        return Map.of(
                "type", p.type().name(),
                "label", p.label(),
                "missed_count", p.missedCount(),
                "total", p.totalCount(),
                "drug_code", p.drugKdCode() == null ? "" : p.drugKdCode());
    }

    private Map<String, Object> toDrugMap(DrugSummary d) {
        return Map.of(
                "kd_code", d.kdCode() == null ? "" : d.kdCode(),
                "name", d.name() == null ? "" : d.name(),
                "efficacy", d.efficacy() == null ? "" : d.efficacy());
    }

    record AiResponse(List<AiInsight> insights) {
        List<InsightDraft> toDrafts() {
            return insights == null ? List.of() : insights.stream()
                    .filter(AiInsight::hasSource)
                    .map(AiInsight::toDraft)
                    .toList();
        }
    }

    record AiInsight(String type, String severity, String title, String description, String source,
                     BigDecimal confidence) {
        boolean hasSource() {
            return source != null && !source.isBlank();
        }

        InsightDraft toDraft() {
            return new InsightDraft(
                    InsightType.valueOf(type),
                    InsightSeverity.valueOf(severity),
                    title,
                    description,
                    source);
        }
    }
}
