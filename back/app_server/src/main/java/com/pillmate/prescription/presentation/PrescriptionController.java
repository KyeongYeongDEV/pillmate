package com.pillmate.prescription.presentation;

import com.pillmate.common.response.ApiResponse;
import com.pillmate.prescription.application.GetPrescriptionDetailUseCase;
import com.pillmate.prescription.application.GetPrescriptionsUseCase;
import com.pillmate.prescription.application.GetUnresolvedCandidatesUseCase;
import com.pillmate.prescription.application.GetUploadUrlUseCase;
import com.pillmate.prescription.application.OcrAndRegisterPrescriptionUseCase;
import com.pillmate.prescription.application.RegisterPrescriptionService;
import com.pillmate.prescription.application.ResolveCandidateUseCase;
import com.pillmate.prescription.application.dto.PrescriptionDetailResponse;
import com.pillmate.prescription.application.dto.PrescriptionSummary;
import com.pillmate.prescription.application.dto.RegisterPrescriptionResponse;
import com.pillmate.prescription.application.dto.UnresolvedCandidateDto;
import com.pillmate.prescription.application.dto.UploadUrlResponse;
import com.pillmate.prescription.presentation.dto.OcrRegisterRequest;
import com.pillmate.prescription.presentation.dto.RegisterPrescriptionRequest;
import com.pillmate.prescription.presentation.dto.ResolveCandidateRequest;
import com.pillmate.common.security.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/prescriptions")
@RequiredArgsConstructor
public class PrescriptionController {

    private final GetUploadUrlUseCase getUploadUrlUseCase;
    private final RegisterPrescriptionService registerPrescriptionService;
    private final OcrAndRegisterPrescriptionUseCase ocrAndRegisterPrescriptionUseCase;
    private final GetUnresolvedCandidatesUseCase getUnresolvedCandidatesUseCase;
    private final ResolveCandidateUseCase resolveCandidateUseCase;
    private final GetPrescriptionsUseCase getPrescriptionsUseCase;
    private final GetPrescriptionDetailUseCase getPrescriptionDetailUseCase;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PrescriptionSummary>>> list() {
        return ResponseEntity.ok(ApiResponse.success(getPrescriptionsUseCase.list()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PrescriptionDetailResponse>> detail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(getPrescriptionDetailUseCase.detail(id)));
    }

    @PostMapping("/upload-url")
    public ResponseEntity<ApiResponse<UploadUrlResponse>> issueUploadUrl() {
        UploadUrlResponse response = getUploadUrlUseCase.issue();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RegisterPrescriptionResponse>> register(
            @Valid @RequestBody RegisterPrescriptionRequest request) {
        RegisterPrescriptionResponse response = registerPrescriptionService.register(
                request.toCommand(UserContext.get()));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/ocr")
    public ResponseEntity<ApiResponse<RegisterPrescriptionResponse>> ocrRegister(
            @Valid @RequestBody OcrRegisterRequest request) {
        RegisterPrescriptionResponse response = ocrAndRegisterPrescriptionUseCase.ocrAndRegister(
                request.prescribedAt(), request.imageKey());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}/candidates")
    public ResponseEntity<ApiResponse<List<UnresolvedCandidateDto>>> getCandidates(
            @PathVariable Long id) {
        List<UnresolvedCandidateDto> candidates = getUnresolvedCandidatesUseCase.getUnresolved(id);
        return ResponseEntity.ok(ApiResponse.success(candidates));
    }

    @PutMapping("/{id}/candidates/{itemIndex}/resolve")
    public ResponseEntity<ApiResponse<Void>> resolveCandidate(
            @PathVariable Long id,
            @PathVariable int itemIndex,
            @Valid @RequestBody ResolveCandidateRequest request) {
        resolveCandidateUseCase.resolve(id, itemIndex, request.selectedDrugId(), null);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
