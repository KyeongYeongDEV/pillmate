package com.pillmate.prescription.presentation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.prescription.application.GetLatestPrescriptionWithInsightUseCase;
import com.pillmate.prescription.application.GetPrescriptionDetailUseCase;
import com.pillmate.prescription.application.GetPrescriptionsUseCase;
import com.pillmate.prescription.application.GetUnresolvedCandidatesUseCase;
import com.pillmate.prescription.application.GetUploadUrlUseCase;
import com.pillmate.prescription.application.ExtractPrescriptionOcrUseCase;
import com.pillmate.prescription.application.RegisterPrescriptionService;
import com.pillmate.prescription.application.ResolveCandidateUseCase;
import com.pillmate.prescription.application.SoftDeletePrescriptionUseCase;
import com.pillmate.prescription.application.UpdatePrescriptionMemoUseCase;
import com.pillmate.prescription.application.dto.LatestPrescriptionWithInsightResponse;
import com.pillmate.prescription.application.dto.PrescriptionDetailResponse;
import com.pillmate.prescription.application.dto.PrescriptionInsightView;
import com.pillmate.prescription.application.dto.PrescriptionSummary;
import com.pillmate.prescription.application.dto.RegisterPrescriptionResponse;
import com.pillmate.prescription.application.dto.RegisteredDrugItem;
import com.pillmate.prescription.application.dto.UnresolvedCandidateDto;
import com.pillmate.prescription.application.dto.UploadUrlResponse;
import com.pillmate.prescription.domain.model.CandidateDecisionType;
import com.pillmate.prescription.domain.model.OcrStatus;
import com.pillmate.prescription.domain.model.PrescriptionInsightSeverity;
import com.pillmate.prescription.domain.model.PrescriptionInsightType;
import com.pillmate.prescription.domain.model.PrescriptionStatus;
import com.pillmate.prescription.presentation.dto.RegisterPrescriptionRequest;
import com.pillmate.prescription.presentation.dto.ResolveCandidateRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.Executor;

@DisplayName("PrescriptionController")
@WebMvcTest(PrescriptionController.class)
class PrescriptionControllerTest {

    @TestConfiguration
    static class TestExecutorConfig {
        @Bean("ocrExecutor")
        Executor ocrExecutor() {
            return command -> command.run();
        }
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean GetUploadUrlUseCase getUploadUrlUseCase;
    @MockitoBean RegisterPrescriptionService registerPrescriptionService;
    @MockitoBean ExtractPrescriptionOcrUseCase extractPrescriptionOcrUseCase;
    @MockitoBean GetUnresolvedCandidatesUseCase getUnresolvedCandidatesUseCase;
    @MockitoBean ResolveCandidateUseCase resolveCandidateUseCase;
    @MockitoBean GetPrescriptionsUseCase getPrescriptionsUseCase;
    @MockitoBean GetPrescriptionDetailUseCase getPrescriptionDetailUseCase;
    @MockitoBean GetLatestPrescriptionWithInsightUseCase getLatestPrescriptionWithInsightUseCase;
    @MockitoBean UpdatePrescriptionMemoUseCase updatePrescriptionMemoUseCase;
    @MockitoBean SoftDeletePrescriptionUseCase softDeletePrescriptionUseCase;

    @Test
    @DisplayName("POST /prescriptions/upload-url → 200 + uploadUrl/objectKey/expiresAt")
    void postUploadUrl_returns200() throws Exception {
        given(getUploadUrlUseCase.issue())
                .willReturn(new UploadUrlResponse(
                        "https://s3.test/key?sig=x",
                        "prescriptions/2026/05/uuid.jpg",
                        Instant.parse("2026-05-23T03:05:00Z")));

        mockMvc.perform(post("/prescriptions/upload-url")
                        .header("X-User-Id", "1"))
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
                        List.of(new RegisteredDrugItem(
                                101L, "KD-001", "타이레놀", "타이레놀500mg",
                                new BigDecimal("0.95"), "https://nedrug/img1"))));

        mockMvc.perform(post("/prescriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "2")
                        .content(objectMapper.writeValueAsString(validRegisterRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.prescriptionId").value(42))
                .andExpect(jsonPath("$.data.ocrStatus").value("DONE"))
                .andExpect(jsonPath("$.data.items[0].drugId").value(101));
    }

    @Test
    @DisplayName("imageKey null + 직접입력 약 → 200, MANUAL 상태로 등록")
    void postRegister_whenImageKeyNullAndDirectInput_returns200WithManualStatus() throws Exception {
        given(registerPrescriptionService.register(any()))
                .willReturn(new RegisterPrescriptionResponse(
                        77L, OcrStatus.MANUAL,
                        List.of(new RegisteredDrugItem(
                                null, null, "타이레놀", null,
                                null, null))));

        RegisterPrescriptionRequest req = new RegisterPrescriptionRequest(
                LocalDate.of(2026, 5, 23),
                null,
                List.of(new RegisterPrescriptionRequest.Item(
                        null, "타이레놀", new BigDecimal("1.00"), "정", 3, 7, null)));

        mockMvc.perform(post("/prescriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "2")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.prescriptionId").value(77))
                .andExpect(jsonPath("$.data.ocrStatus").value("MANUAL"));
    }

    @Test
    @DisplayName("POST /prescriptions items 비어있으면 400")
    void postRegister_returns400_whenItemsEmpty() throws Exception {
        RegisterPrescriptionRequest req = new RegisterPrescriptionRequest(
                LocalDate.of(2026, 5, 23), "prescriptions/2026/05/uuid.jpg", List.of());

        mockMvc.perform(post("/prescriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "2")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("kdCode 1건 매칭 실패 시 200 + ocrStatus MANUAL + drugId null")
    void postRegister_whenOneItemKdCodeNotFound_returns200_withManualStatus_andNullDrugId() throws Exception {
        given(registerPrescriptionService.register(any()))
                .willReturn(new RegisterPrescriptionResponse(
                        99L, OcrStatus.MANUAL,
                        List.of(
                                new RegisteredDrugItem(
                                        101L, "KD-001", "타이레놀", "타이레놀500mg",
                                        new BigDecimal("0.95"), "https://nedrug/img1"),
                                new RegisteredDrugItem(
                                        null, null, "동광나자티딘캡슐", null,
                                        new BigDecimal("0.95"), null))));

        mockMvc.perform(post("/prescriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "2")
                        .content(objectMapper.writeValueAsString(validRegisterRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.prescriptionId").value(99))
                .andExpect(jsonPath("$.data.ocrStatus").value("MANUAL"))
                .andExpect(jsonPath("$.data.items[0].drugId").value(101))
                .andExpect(jsonPath("$.data.items[1].drugId").doesNotExist())
                .andExpect(jsonPath("$.data.items[1].nameRaw").value("동광나자티딘캡슐"));
    }

    @Test
    @DisplayName("GET /prescriptions/{id}/candidates → 200 + 미해결 후보 목록")
    void getCandidates_returns200() throws Exception {
        given(getUnresolvedCandidatesUseCase.getUnresolved(1L))
                .willReturn(List.of(new UnresolvedCandidateDto(
                        null, 0, CandidateDecisionType.CONFIRM, "ambiguous",
                        "[{\"drugId\":12320}]")));

        mockMvc.perform(get("/prescriptions/1/candidates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].decisionType").value("CONFIRM"))
                .andExpect(jsonPath("$.data[0].reason").value("ambiguous"));
    }

    @Test
    @DisplayName("PUT /prescriptions/{id}/candidates/{idx}/resolve → 200")
    void resolveCandidate_returns200() throws Exception {
        doNothing().when(resolveCandidateUseCase).resolve(anyLong(), anyInt(), anyLong(), any());

        mockMvc.perform(put("/prescriptions/1/candidates/0/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResolveCandidateRequest(12320L))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /prescriptions → 200 + 본인 처방전 목록")
    void getList_returns200() throws Exception {
        given(getPrescriptionsUseCase.list()).willReturn(List.of(
                new PrescriptionSummary(42L, LocalDate.of(2026, 6, 10), OcrStatus.DONE,
                        2, "타이레놀, 아스피린", Instant.parse("2026-06-10T01:00:00Z"),
                        null, null, PrescriptionStatus.ONGOING, null, null, null, null, null)));

        mockMvc.perform(get("/prescriptions").header("X-User-Id", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(42))
                .andExpect(jsonPath("$.data[0].ocrStatus").value("DONE"))
                .andExpect(jsonPath("$.data[0].drugCount").value(2))
                .andExpect(jsonPath("$.data[0].drugNames").value("타이레놀, 아스피린"));
    }

    @Test
    @DisplayName("GET /prescriptions/{id} → 200 + 상세 + presigned imageUrl + drugs")
    void getDetail_returns200() throws Exception {
        given(getPrescriptionDetailUseCase.detail(42L)).willReturn(
                new PrescriptionDetailResponse(42L, LocalDate.of(2026, 6, 10), OcrStatus.DONE,
                        "https://s3.test/presigned?sig=x",
                        List.of(new PrescriptionDetailResponse.DrugDetail(
                                "타이레놀", "타이레놀정500밀리그램", "KD-001",
                                new BigDecimal("1.00"), "정", 3, 7, new BigDecimal("0.95"), null, null)),
                        null, null, null, PrescriptionStatus.ONGOING, null, null, null, null, null, null));

        mockMvc.perform(get("/prescriptions/42").header("X-User-Id", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(42))
                .andExpect(jsonPath("$.data.imageUrl").value("https://s3.test/presigned?sig=x"))
                .andExpect(jsonPath("$.data.drugs[0].matchedDrugName").value("타이레놀정500밀리그램"));
    }

    @Test
    @DisplayName("GET /prescriptions/latest-with-insight → 200 + 최신 처방전 + insight")
    void getLatestWithInsight_returns200() throws Exception {
        given(getLatestPrescriptionWithInsightUseCase.loadLatestForPatient(7L)).willReturn(
                new LatestPrescriptionWithInsightResponse(42L, LocalDate.of(2026, 6, 10), 3, "메트포르민정",
                        List.of(new PrescriptionInsightView(1L, PrescriptionInsightType.RECOMMENDATION,
                                PrescriptionInsightSeverity.INFO, "비타민 B12 영향 가능",
                                "장기 복용 시 흡수에 영향을 줄 수 있어요.", "식약처", new BigDecimal("0.90")))));

        mockMvc.perform(get("/prescriptions/latest-with-insight").header("X-User-Id", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.prescriptionId").value(42))
                .andExpect(jsonPath("$.data.drugCount").value(3))
                .andExpect(jsonPath("$.data.primaryDrugName").value("메트포르민정"))
                .andExpect(jsonPath("$.data.insights[0].source").value("식약처"));
    }

    @Test
    @DisplayName("GET /prescriptions/latest-with-insight insight 없음 → 200 + null data")
    void getLatestWithInsight_noData_returns200() throws Exception {
        given(getLatestPrescriptionWithInsightUseCase.loadLatestForPatient(7L)).willReturn(null);

        mockMvc.perform(get("/prescriptions/latest-with-insight").header("X-User-Id", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("GET /prescriptions/latest-with-insight 타인 → 403 PATIENT_ACCESS_DENIED")
    void getLatestWithInsight_otherPatient_returns403() throws Exception {
        given(getLatestPrescriptionWithInsightUseCase.loadLatestForPatient(anyLong()))
                .willThrow(new PillmateException(ErrorCode.PATIENT_ACCESS_DENIED));

        mockMvc.perform(get("/prescriptions/latest-with-insight").header("X-User-Id", "99"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PILL_016"));
    }

    @Test
    @DisplayName("GET /prescriptions/{id} 타인 처방전 → 403 PATIENT_ACCESS_DENIED")
    void getDetail_otherPatient_returns403() throws Exception {
        given(getPrescriptionDetailUseCase.detail(42L))
                .willThrow(new PillmateException(ErrorCode.PATIENT_ACCESS_DENIED));

        mockMvc.perform(get("/prescriptions/42").header("X-User-Id", "99"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PILL_016"));
    }

    @Test
    @DisplayName("kdCode null + nameRaw 있는 item → 200, MANUAL 상태로 등록")
    void postRegister_whenKdCodeNullAndNameRawPresent_returns200WithManualStatus() throws Exception {
        given(registerPrescriptionService.register(any()))
                .willReturn(new RegisterPrescriptionResponse(
                        99L, OcrStatus.MANUAL,
                        List.of(new RegisteredDrugItem(
                                null, null, "동광나자티딘캡슐", null,
                                new BigDecimal("0.95"), null))));

        RegisterPrescriptionRequest req = new RegisterPrescriptionRequest(
                LocalDate.of(2026, 5, 23),
                "prescriptions/2026/05/uuid.jpg",
                List.of(new RegisterPrescriptionRequest.Item(
                        null, "동광나자티딘캡슐", new BigDecimal("150"), "mg", 2, 7, new BigDecimal("0.95"))));

        mockMvc.perform(post("/prescriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "2")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.prescriptionId").value(99))
                .andExpect(jsonPath("$.data.ocrStatus").value("MANUAL"))
                .andExpect(jsonPath("$.data.items[0].nameRaw").value("동광나자티딘캡슐"));
    }

    @Test
    @DisplayName("nameRaw 빈 값 → 400 (약 이름 필수)")
    void postRegister_whenNameRawBlank_returns400() throws Exception {
        RegisterPrescriptionRequest req = new RegisterPrescriptionRequest(
                LocalDate.of(2026, 5, 23),
                "prescriptions/2026/05/uuid.jpg",
                List.of(new RegisterPrescriptionRequest.Item(
                        "KD-001", "", new BigDecimal("1.00"), "정", 3, 7, new BigDecimal("0.95"))));

        mockMvc.perform(post("/prescriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "2")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH /prescriptions/{id} → 200 OK")
    void patchMemo_returns200() throws Exception {
        doNothing().when(updatePrescriptionMemoUseCase).update(anyLong(), any(), any(), any());

        mockMvc.perform(patch("/prescriptions/42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "7")
                        .content("{\"label\":\"아침약\",\"memo\":\"식후 30분\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH /prescriptions/{id} 타인 처방전 → 403")
    void patchMemo_otherPatient_returns403() throws Exception {
        org.mockito.Mockito.doThrow(new PillmateException(ErrorCode.PATIENT_ACCESS_DENIED))
                .when(updatePrescriptionMemoUseCase).update(anyLong(), any(), any(), any());

        mockMvc.perform(patch("/prescriptions/42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "99")
                        .content("{\"label\":\"X\",\"memo\":\"Y\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PILL_016"));
    }

    @Test
    @DisplayName("DELETE /prescriptions/{id} → 200 OK (소프트 삭제)")
    void delete_ownPrescription_returns200() throws Exception {
        doNothing().when(softDeletePrescriptionUseCase).delete(42L);

        mockMvc.perform(delete("/prescriptions/42")
                        .header("X-User-Id", "7"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /prescriptions/{id} 타인 처방전 → 403 PATIENT_ACCESS_DENIED")
    void delete_otherPatient_returns403() throws Exception {
        org.mockito.Mockito.doThrow(new PillmateException(ErrorCode.PATIENT_ACCESS_DENIED))
                .when(softDeletePrescriptionUseCase).delete(42L);

        mockMvc.perform(delete("/prescriptions/42")
                        .header("X-User-Id", "99"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PILL_016"));
    }

    private RegisterPrescriptionRequest validRegisterRequest() {
        return new RegisterPrescriptionRequest(
                LocalDate.of(2026, 5, 23),
                "prescriptions/2026/05/uuid.jpg",
                List.of(new RegisterPrescriptionRequest.Item(
                        "KD-001", "타이레놀", new BigDecimal("1.00"), "정", 3, 7, new BigDecimal("0.95"))));
    }
}
