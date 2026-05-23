package com.pillmate.prescription.presentation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.prescription.application.GetUploadUrlUseCase;
import com.pillmate.prescription.application.OcrAndRegisterPrescriptionUseCase;
import com.pillmate.prescription.application.RegisterPrescriptionService;
import com.pillmate.prescription.application.dto.RegisterPrescriptionResponse;
import com.pillmate.prescription.application.dto.RegisteredDrugItem;
import com.pillmate.prescription.application.dto.UploadUrlResponse;
import com.pillmate.prescription.application.exception.DrugNotMatchedException;
import com.pillmate.prescription.domain.model.OcrStatus;
import com.pillmate.prescription.presentation.dto.OcrRegisterRequest;
import com.pillmate.prescription.presentation.dto.RegisterPrescriptionRequest;
import com.pillmate.prescription.presentation.dto.UploadUrlRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("PrescriptionController")
@WebMvcTest(PrescriptionController.class)
class PrescriptionControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean GetUploadUrlUseCase getUploadUrlUseCase;
    @MockitoBean RegisterPrescriptionService registerPrescriptionService;
    @MockitoBean OcrAndRegisterPrescriptionUseCase ocrAndRegisterPrescriptionUseCase;

    @Test
    @DisplayName("POST /prescriptions/upload-url → 200 + uploadUrl/objectKey/expiresAt")
    void postUploadUrl_returns200() throws Exception {
        given(getUploadUrlUseCase.issue(1L))
                .willReturn(new UploadUrlResponse(
                        "https://s3.test/key?sig=x",
                        "prescriptions/2026/05/uuid.jpg",
                        Instant.parse("2026-05-23T03:05:00Z")));

        mockMvc.perform(post("/prescriptions/upload-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UploadUrlRequest(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uploadUrl").value("https://s3.test/key?sig=x"))
                .andExpect(jsonPath("$.data.objectKey").value("prescriptions/2026/05/uuid.jpg"))
                .andExpect(jsonPath("$.data.expiresAt").exists());
    }

    @Test
    @DisplayName("POST /prescriptions → 200 + prescriptionId 반환")
    void postRegister_returns200() throws Exception {
        given(registerPrescriptionService.register(any()))
                .willReturn(new RegisterPrescriptionResponse(
                        42L, OcrStatus.DONE,
                        List.of(new RegisteredDrugItem(101L, "KD-001", "타이레놀", new BigDecimal("0.95")))));

        mockMvc.perform(post("/prescriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRegisterRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.prescriptionId").value(42))
                .andExpect(jsonPath("$.data.ocrStatus").value("DONE"))
                .andExpect(jsonPath("$.data.items[0].drugId").value(101));
    }

    @Test
    @DisplayName("POST /prescriptions/ocr → 200 + prescriptionId 반환")
    void postOcrRegister_returns200() throws Exception {
        given(ocrAndRegisterPrescriptionUseCase.ocrAndRegister(anyLong(), anyLong(), any(), anyString()))
                .willReturn(new RegisterPrescriptionResponse(
                        42L, OcrStatus.DONE,
                        List.of(new RegisteredDrugItem(101L, "KD-001", "타이레놀", new BigDecimal("0.95")))));

        OcrRegisterRequest req = new OcrRegisterRequest(1L, 2L, LocalDate.of(2026, 5, 23), "imageKey");

        mockMvc.perform(post("/prescriptions/ocr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.prescriptionId").value(42));
    }

    @Test
    @DisplayName("POST /prescriptions/ocr → 504 when upstream timeout")
    void postOcrRegister_returns504_whenUpstreamTimeout() throws Exception {
        given(ocrAndRegisterPrescriptionUseCase.ocrAndRegister(anyLong(), anyLong(), any(), anyString()))
                .willThrow(new PillmateException(ErrorCode.OCR_UPSTREAM_TIMEOUT));

        OcrRegisterRequest req = new OcrRegisterRequest(1L, 2L, LocalDate.of(2026, 5, 23), "imageKey");

        mockMvc.perform(post("/prescriptions/ocr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.error.code").value("PILL_050"));
    }

    @Test
    @DisplayName("POST /prescriptions items 비어있으면 400")
    void postRegister_returns400_whenItemsEmpty() throws Exception {
        RegisterPrescriptionRequest req = new RegisterPrescriptionRequest(
                1L, 2L, LocalDate.of(2026, 5, 23), "prescriptions/2026/05/uuid.jpg", List.of());

        mockMvc.perform(post("/prescriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("kdCode 매칭 실패 시 400 + PILL_021")
    void postRegister_returns400_whenDrugNotMatched() throws Exception {
        given(registerPrescriptionService.register(any()))
                .willThrow(new DrugNotMatchedException("KD-XXX"));

        mockMvc.perform(post("/prescriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRegisterRequest())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PILL_021"));
    }

    private RegisterPrescriptionRequest validRegisterRequest() {
        return new RegisterPrescriptionRequest(
                1L, 2L, LocalDate.of(2026, 5, 23),
                "prescriptions/2026/05/uuid.jpg",
                List.of(new RegisterPrescriptionRequest.Item(
                        "KD-001", "타이레놀", new BigDecimal("1.00"), "정", 3, 7, new BigDecimal("0.95"))));
    }
}
