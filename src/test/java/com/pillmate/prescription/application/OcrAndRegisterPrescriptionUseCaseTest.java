package com.pillmate.prescription.application;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.prescription.application.dto.RegisterPrescriptionCommand;
import com.pillmate.prescription.application.dto.RegisterPrescriptionResponse;
import com.pillmate.prescription.domain.model.OcrStatus;
import com.pillmate.prescription.application.port.FileStoragePort;
import com.pillmate.common.security.CareGroupGuard;
import com.pillmate.prescription.application.port.OcrPort;
import com.pillmate.prescription.application.port.OcrPort.OcrItem;
import com.pillmate.prescription.application.port.OcrPort.OcrResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OcrAndRegisterPrescriptionUseCaseTest {

    @InjectMocks
    private OcrAndRegisterPrescriptionUseCase ocrAndRegisterPrescriptionUseCase;

    @Mock
    private FileStoragePort fileStoragePort;

    @Mock
    private OcrPort ocrPort;

    @Mock
    private RegisterPrescriptionService registerPrescriptionService;

    @Mock
    private CareGroupGuard careGroupGuard;

    @Test
    @DisplayName("OCR 결과가 있으면 처방전을 등록하고 응답을 반환한다")
    void ocrAndRegister_whenAiServerReturnsItems_callsRegisterAndReturnsResponse() {
        // given
        Long careGroupId = 1L;
        Long patientId = 2L;
        LocalDate prescribedAt = LocalDate.of(2026, 5, 23);
        String imageKey = "prescriptions/2026/05/uuid.jpg";
        String downloadUrl = "http://presigned-get-url";

        given(fileStoragePort.issueDownloadUrl(eq(imageKey), any(Duration.class))).willReturn(downloadUrl);

        OcrItem item = new OcrItem("123", "활명수", "활명수", BigDecimal.ONE, "병", 3, 3, new BigDecimal("0.95"), null);
        given(ocrPort.extractFromImage(downloadUrl)).willReturn(new OcrResult(List.of(item), "식약처"));

        RegisterPrescriptionResponse expectedResponse = new RegisterPrescriptionResponse(10L, com.pillmate.prescription.domain.model.OcrStatus.DONE, Collections.emptyList());
        given(registerPrescriptionService.register(any(RegisterPrescriptionCommand.class))).willReturn(expectedResponse);

        // when
        RegisterPrescriptionResponse actualResponse = ocrAndRegisterPrescriptionUseCase.ocrAndRegister(careGroupId, patientId, prescribedAt, imageKey);

        // then
        assertThat(actualResponse).isEqualTo(expectedResponse);
        verify(fileStoragePort).issueDownloadUrl(eq(imageKey), eq(Duration.ofMinutes(10)));
        
        ArgumentCaptor<RegisterPrescriptionCommand> captor = ArgumentCaptor.forClass(RegisterPrescriptionCommand.class);
        verify(registerPrescriptionService).register(captor.capture());
        
        RegisterPrescriptionCommand command = captor.getValue();
        assertThat(command.careGroupId()).isEqualTo(careGroupId);
        assertThat(command.patientId()).isEqualTo(patientId);
        assertThat(command.items()).hasSize(1);
        assertThat(command.items().get(0).kdCode()).isEqualTo("123");
    }

    @Test
    @DisplayName("ai_server 가 일부 약만 매칭(kdCode null 혼합)을 반환해도 register 위임은 그대로 호출되고 MANUAL 응답이 전달된다")
    void ocrAndRegister_whenAiServerReturnsPartialMatch_savesAsManual() {
        given(fileStoragePort.issueDownloadUrl(any(), any())).willReturn("http://url");
        OcrItem matched = new OcrItem(
                "200500823", "오페나딘서방정50밀리그람", "오페나딘서방정50밀리그램",
                new BigDecimal("50"), "mg", 2, 7, new BigDecimal("0.95"), null);
        OcrItem unmatched = new OcrItem(
                null, "동광나자티딘캡슐150mg Nizatidine 150mg", null,
                new BigDecimal("150"), "mg", 2, 7, new BigDecimal("0.95"), null);
        given(ocrPort.extractFromImage(any()))
                .willReturn(new OcrResult(List.of(matched, unmatched), "식약처"));

        RegisterPrescriptionResponse manual = new RegisterPrescriptionResponse(
                7L, OcrStatus.MANUAL, Collections.emptyList());
        given(registerPrescriptionService.register(any(RegisterPrescriptionCommand.class)))
                .willReturn(manual);

        RegisterPrescriptionResponse actual = ocrAndRegisterPrescriptionUseCase.ocrAndRegister(
                1L, 2L, LocalDate.of(2026, 5, 24), "prescriptions/2026/05/uuid.jpg");

        assertThat(actual.ocrStatus()).isEqualTo(OcrStatus.MANUAL);
        ArgumentCaptor<RegisterPrescriptionCommand> captor =
                ArgumentCaptor.forClass(RegisterPrescriptionCommand.class);
        verify(registerPrescriptionService).register(captor.capture());
        assertThat(captor.getValue().items()).hasSize(2);
        assertThat(captor.getValue().items().get(1).kdCode()).isNull();
    }

    @Test
    @DisplayName("OCR 결과가 비어있으면 OCR_EMPTY 예외를 던진다")
    void ocrAndRegister_whenAiServerReturnsEmpty_throwsOcrEmpty() {
        // given
        given(fileStoragePort.issueDownloadUrl(any(), any())).willReturn("http://url");
        given(ocrPort.extractFromImage(any())).willReturn(new OcrResult(Collections.emptyList(), "unknown"));

        // when & then
        assertThatThrownBy(() -> ocrAndRegisterPrescriptionUseCase.ocrAndRegister(1L, 2L, LocalDate.now(), "key"))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OCR_EMPTY);
    }
}
