package com.pillmate.prescription.application.listener;

import com.pillmate.prescription.application.PrescriptionInsightContextReader;
import com.pillmate.prescription.application.PrescriptionInsightContextReader.RecommendationContext;
import com.pillmate.prescription.application.PrescriptionInsightPersistenceService;
import com.pillmate.prescription.application.port.PrescriptionRecommendationPort;
import com.pillmate.prescription.application.port.PrescriptionRecommendationPort.DrugContext;
import com.pillmate.prescription.application.port.PrescriptionRecommendationPort.InsightDraft;
import com.pillmate.prescription.domain.event.PrescriptionRegistered;
import com.pillmate.prescription.domain.model.PrescriptionInsight;
import com.pillmate.prescription.domain.model.PrescriptionInsightSeverity;
import com.pillmate.prescription.domain.model.PrescriptionInsightType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("PrescriptionRecommendationListener — 등록 후 AI 추천 생성/영속")
class PrescriptionRecommendationListenerTest {

    private static final Long PRESCRIPTION_ID = 42L;
    private static final Long PATIENT_ID = 7L;

    @Mock PrescriptionInsightContextReader contextReader;
    @Mock PrescriptionRecommendationPort recommendationPort;
    @Mock PrescriptionInsightPersistenceService persistenceService;

    private PrescriptionRecommendationListener sut;

    @BeforeEach
    void setUp() {
        sut = new PrescriptionRecommendationListener(contextReader, recommendationPort, persistenceService);
    }

    private RecommendationContext context() {
        return new RecommendationContext(PATIENT_ID,
                List.of(new DrugContext("200500823", "메트포르민정", new BigDecimal("1.00"), "정", 2, 30)));
    }

    private InsightDraft draft() {
        return new InsightDraft(PrescriptionInsightType.RECOMMENDATION, PrescriptionInsightSeverity.INFO,
                "비타민 B12 영향 가능", "장기 복용 시 흡수에 영향을 줄 수 있어요.", "식약처", new BigDecimal("0.90"));
    }

    @Test
    @DisplayName("draft 생성되면 PrescriptionInsight 로 변환 후 저장")
    void on_withDrafts_savesInsights() {
        given(contextReader.load(PRESCRIPTION_ID)).willReturn(Optional.of(context()));
        given(recommendationPort.generate(eq(PRESCRIPTION_ID), eq(PATIENT_ID), anyList()))
                .willReturn(List.of(draft()));

        sut.on(new PrescriptionRegistered(PATIENT_ID, PRESCRIPTION_ID));

        ArgumentCaptor<List<PrescriptionInsight>> captor = ArgumentCaptor.forClass(List.class);
        verify(persistenceService).saveAll(captor.capture());
        List<PrescriptionInsight> saved = captor.getValue();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getPrescriptionId()).isEqualTo(PRESCRIPTION_ID);
        assertThat(saved.get(0).getSource()).isEqualTo("식약처");
    }

    @Test
    @DisplayName("draft 비어있으면 저장 호출 안 함")
    void on_noDrafts_doesNotSave() {
        given(contextReader.load(PRESCRIPTION_ID)).willReturn(Optional.of(context()));
        given(recommendationPort.generate(anyLong(), anyLong(), anyList())).willReturn(List.of());

        sut.on(new PrescriptionRegistered(PATIENT_ID, PRESCRIPTION_ID));

        verify(persistenceService, never()).saveAll(any());
    }

    @Test
    @DisplayName("처방전 없으면 (context empty) port 호출 안 함")
    void on_contextMissing_skips() {
        given(contextReader.load(PRESCRIPTION_ID)).willReturn(Optional.empty());

        sut.on(new PrescriptionRegistered(PATIENT_ID, PRESCRIPTION_ID));

        verify(recommendationPort, never()).generate(anyLong(), anyLong(), anyList());
        verify(persistenceService, never()).saveAll(any());
    }

    @Test
    @DisplayName("graceful — port 예외 발생해도 리스너는 예외 전파 안 함 (등록 흐름 보호)")
    void on_portThrows_swallowsException() {
        given(contextReader.load(PRESCRIPTION_ID)).willReturn(Optional.of(context()));
        given(recommendationPort.generate(anyLong(), anyLong(), anyList()))
                .willThrow(new RuntimeException("ai down"));

        assertThatCode(() -> sut.on(new PrescriptionRegistered(PATIENT_ID, PRESCRIPTION_ID)))
                .doesNotThrowAnyException();
        verify(persistenceService, never()).saveAll(any());
    }
}
