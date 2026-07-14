package com.pillmate.prescription.infrastructure.ai;

import com.pillmate.common.config.AiServerProperties;
import com.pillmate.prescription.application.port.PrescriptionRecommendationPort;
import com.pillmate.prescription.domain.model.PrescriptionInsightSeverity;
import com.pillmate.prescription.domain.model.PrescriptionInsightType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Slf4j
@Component
public class AiServerRecommendationClient implements PrescriptionRecommendationPort {

    private static final String ENDPOINT = "/api/v1/analyze/prescription-recommendation";

    private final RestClient restClient;

    public AiServerRecommendationClient(RestClient.Builder builder,
                                        AiServerProperties properties) {
        this.restClient = builder.baseUrl(properties.baseUrl()).build();
    }

    @Override
    public List<InsightDraft> generate(Long prescriptionId, Long patientId, List<DrugContext> drugs) {
        try {
            AiResponse response = restClient.post()
                    .uri(ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(toRequest(prescriptionId, patientId, drugs))
                    .retrieve()
                    .body(AiResponse.class);
            return response == null ? List.of() : response.toDrafts();
        } catch (RestClientException e) {
            log.warn("recommendation ai-server call failed prescriptionId={} reason={}",
                    prescriptionId, e.getClass().getSimpleName());
            return List.of();
        }
    }

    private AiRequest toRequest(Long prescriptionId, Long patientId, List<DrugContext> drugs) {
        List<AiDrug> aiDrugs = drugs.stream().map(this::toAiDrug).toList();
        return new AiRequest(prescriptionId, patientId, aiDrugs);
    }

    private AiDrug toAiDrug(DrugContext d) {
        return new AiDrug(
                Objects.requireNonNullElse(d.code(), ""),
                Objects.requireNonNullElse(d.name(), ""),
                d.doseAmount(),
                Objects.requireNonNullElse(d.doseUnit(), ""),
                d.frequency(), d.durationDays());
    }

    private record AiRequest(Long prescriptionId, Long patientId, List<AiDrug> drugs) {}

    private record AiDrug(String code, String name, BigDecimal dose_amount, String dose_unit,
                          Integer frequency, Integer duration_days) {}

    private record AiResponse(List<AiInsight> insights) {
        List<InsightDraft> toDrafts() {
            return insights == null ? List.of() : insights.stream()
                    .filter(AiInsight::isValid)
                    .map(AiInsight::toDraft)
                    .toList();
        }
    }

    private record AiInsight(String type, String severity, String title, String description,
                            String source, BigDecimal confidence) {
        boolean isValid() {
            return source != null && !source.isBlank() && confidence != null;
        }

        InsightDraft toDraft() {
            return new InsightDraft(
                    PrescriptionInsightType.valueOf(type),
                    PrescriptionInsightSeverity.valueOf(severity),
                    title, description, source, confidence);
        }
    }
}
