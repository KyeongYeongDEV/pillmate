package com.pillmate.prescription.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pillmate.prescription.application.dto.DrugItem;
import com.pillmate.prescription.application.dto.InteractionWarning;
import com.pillmate.prescription.application.dto.RegisterPrescriptionCommand;
import com.pillmate.prescription.application.dto.RegisterPrescriptionResponse;
import com.pillmate.prescription.application.port.DrugLookupPort;
import com.pillmate.prescription.domain.model.InteractionSeverity;
import com.pillmate.prescription.domain.model.OcrStatus;
import com.pillmate.prescription.domain.model.Prescription;
import com.pillmate.prescription.domain.event.DdiCriticalDetected;
import com.pillmate.prescription.domain.repository.PrescriptionRepository;
import com.pillmate.common.security.UserContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@DisplayName("RegisterPrescription DDI 통합 — 병용금기 경고 첨부")
@ExtendWith(MockitoExtension.class)
class RegisterPrescriptionWithDdiTest {

    @Mock PrescriptionRepository prescriptionRepository;
    @Mock DrugLookupPort drugLookupPort;
    @Mock ObjectMapper objectMapper;
    @Mock CheckInteractionsUseCase checkInteractionsUseCase;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks RegisterPrescriptionService sut;

    @BeforeEach
    void setUp() {
        UserContext.set(2L);
        given(prescriptionRepository.save(any(Prescription.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(drugLookupPort.findByKdCode("200500823"))
                .willReturn(Optional.of(new DrugLookupPort.DrugSummary(1L, "200500823", "이트라코나졸캡슐", null)));
        given(drugLookupPort.findByKdCode("200300001"))
                .willReturn(Optional.of(new DrugLookupPort.DrugSummary(2L, "200300001", "심바스타틴정", null)));
    }

    @Test
    @DisplayName("HIGH 경고 시 response.warnings 에 InteractionWarning 포함, ocrStatus DONE 유지")
    void register_attachesWarnings_whenInteractionFound() {
        // given
        InteractionWarning warning = new InteractionWarning(
                "200500823", "200300001", "이트라코나졸캡슐", "심바스타틴정",
                InteractionSeverity.HIGH, "근육통 가능", "식품의약품안전처");
        given(checkInteractionsUseCase.check(anyList())).willReturn(List.of(warning));

        // when
        RegisterPrescriptionResponse response = sut.register(command());

        // then
        assertThat(response.ocrStatus()).isEqualTo(OcrStatus.DONE);
        assertThat(response.warnings()).hasSize(1);
        assertThat(response.warnings().get(0).severity()).isEqualTo(InteractionSeverity.HIGH);
    }

    @Test
    @DisplayName("CRITICAL 경고 시 ocrStatus MANUAL 로 강제 변경")
    void register_whenCriticalWarning_forcesManual() {
        // given
        InteractionWarning critical = new InteractionWarning(
                "200500823", "200300001", "이트라코나졸캡슐", "심바스타틴정",
                InteractionSeverity.CRITICAL, "횡문근융해증 위험", "식품의약품안전처");
        given(checkInteractionsUseCase.check(anyList())).willReturn(List.of(critical));

        // when
        RegisterPrescriptionResponse response = sut.register(command());

        // then
        assertThat(response.ocrStatus()).isEqualTo(OcrStatus.MANUAL);
        assertThat(response.warnings()).hasSize(1);
        assertThat(response.warnings().get(0).severity()).isEqualTo(InteractionSeverity.CRITICAL);
        verify(eventPublisher).publishEvent(any(DdiCriticalDetected.class));
    }

    private RegisterPrescriptionCommand command() {
        List<DrugItem> items = List.of(
                new DrugItem("200500823", "이트라코나졸캡슐", BigDecimal.ONE, "캡슐", 1, 7, new BigDecimal("0.95")),
                new DrugItem("200300001", "심바스타틴정", BigDecimal.ONE, "정", 1, 7, new BigDecimal("0.95")));
        return new RegisterPrescriptionCommand(2L, LocalDate.of(2026, 5, 26), null, items);
    }
}
