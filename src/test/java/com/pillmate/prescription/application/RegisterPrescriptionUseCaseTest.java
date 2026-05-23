package com.pillmate.prescription.application;

import com.pillmate.prescription.application.dto.DrugItem;
import com.pillmate.prescription.application.dto.RegisterPrescriptionCommand;
import com.pillmate.prescription.application.dto.RegisterPrescriptionResponse;
import com.pillmate.prescription.application.exception.DrugNotMatchedException;
import com.pillmate.prescription.application.exception.EmptyPrescriptionItemsException;
import com.pillmate.prescription.application.port.DrugLookupPort;
import com.pillmate.prescription.domain.model.OcrStatus;
import com.pillmate.prescription.domain.model.Prescription;
import com.pillmate.prescription.domain.repository.PrescriptionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@DisplayName("RegisterPrescriptionUseCase — 처방전 등록")
@ExtendWith(MockitoExtension.class)
class RegisterPrescriptionUseCaseTest {

    @Mock PrescriptionRepository prescriptionRepository;
    @Mock DrugLookupPort drugLookupPort;
    @InjectMocks RegisterPrescriptionService sut;

    @Test
    @DisplayName("모든 kdCode 매칭 성공 + 모든 confidence ≥ 0.7 → DONE 상태로 저장")
    void register_whenAllKdCodesFound_savesAndReturnsDone() {
        DrugItem item = drugItem("KD-001", new BigDecimal("0.92"));
        RegisterPrescriptionCommand command = command(List.of(item));
        given(drugLookupPort.findByKdCode("KD-001"))
                .willReturn(Optional.of(new DrugLookupPort.DrugSummary(101L, "KD-001", "타이레놀")));
        given(prescriptionRepository.save(any(Prescription.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        RegisterPrescriptionResponse response = sut.register(command);

        verify(prescriptionRepository).save(any(Prescription.class));
        assertThat(response.ocrStatus()).isEqualTo(OcrStatus.DONE);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).drugId()).isEqualTo(101L);
        assertThat(response.items().get(0).kdCode()).isEqualTo("KD-001");
    }

    @Test
    @DisplayName("kdCode 가 식약처 DB에 없으면 DrugNotMatchedException")
    void register_whenAnyKdCodeMissing_throwsDrugNotMatched() {
        DrugItem item = drugItem("KD-XXX", new BigDecimal("0.95"));
        RegisterPrescriptionCommand command = command(List.of(item));
        given(drugLookupPort.findByKdCode("KD-XXX")).willReturn(Optional.empty());

        assertThatThrownBy(() -> sut.register(command))
                .isInstanceOf(DrugNotMatchedException.class);
    }

    @Test
    @DisplayName("confidence 가 0.7 미만 항목 포함 시 MANUAL 상태로 저장")
    void register_whenLowConfidence_marksManual() {
        DrugItem high = drugItem("KD-001", new BigDecimal("0.95"));
        DrugItem low = drugItem("KD-002", new BigDecimal("0.55"));
        RegisterPrescriptionCommand command = command(List.of(high, low));
        given(drugLookupPort.findByKdCode("KD-001"))
                .willReturn(Optional.of(new DrugLookupPort.DrugSummary(101L, "KD-001", "타이레놀")));
        given(drugLookupPort.findByKdCode("KD-002"))
                .willReturn(Optional.of(new DrugLookupPort.DrugSummary(102L, "KD-002", "아스피린")));
        given(prescriptionRepository.save(any(Prescription.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        RegisterPrescriptionResponse response = sut.register(command);

        assertThat(response.ocrStatus()).isEqualTo(OcrStatus.MANUAL);
    }

    @Test
    @DisplayName("items 가 비어 있으면 EmptyPrescriptionItemsException")
    void register_whenItemsEmpty_throws() {
        RegisterPrescriptionCommand command = command(List.of());

        assertThatThrownBy(() -> sut.register(command))
                .isInstanceOf(EmptyPrescriptionItemsException.class);
    }

    private DrugItem drugItem(String kdCode, BigDecimal confidence) {
        return new DrugItem(
                kdCode,
                "약명",
                new BigDecimal("1.00"),
                "정",
                3,
                7,
                confidence);
    }

    private RegisterPrescriptionCommand command(List<DrugItem> items) {
        return new RegisterPrescriptionCommand(
                1L, 2L, LocalDate.of(2026, 5, 23), "prescriptions/uuid.jpg", items);
    }
}
