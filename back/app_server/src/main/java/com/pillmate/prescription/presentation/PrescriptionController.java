package com.pillmate.prescription.presentation;

import com.pillmate.common.response.ApiResponse;
import com.pillmate.prescription.application.GetLatestPrescriptionWithInsightUseCase;
import com.pillmate.prescription.application.GetPrescriptionDetailUseCase;
import com.pillmate.prescription.application.GetPrescriptionsUseCase;
import com.pillmate.prescription.application.GetUnresolvedCandidatesUseCase;
import com.pillmate.prescription.application.GetUploadUrlUseCase;
import com.pillmate.prescription.application.ExtractPrescriptionOcrUseCase;
import com.pillmate.prescription.application.RegisterPrescriptionService;
import com.pillmate.prescription.application.ResolveCandidateUseCase;
import com.pillmate.prescription.application.dto.LatestPrescriptionWithInsightResponse;
import com.pillmate.prescription.application.dto.OcrExtractResponse;
import com.pillmate.prescription.application.dto.PrescriptionDetailResponse;
import com.pillmate.prescription.application.dto.PrescriptionSummary;
import com.pillmate.prescription.application.dto.RegisterPrescriptionResponse;
import com.pillmate.prescription.application.dto.UnresolvedCandidateDto;
import com.pillmate.prescription.application.dto.UploadUrlResponse;
import com.pillmate.prescription.presentation.dto.OcrExtractRequest;
import com.pillmate.prescription.presentation.dto.RegisterPrescriptionRequest;
import com.pillmate.prescription.application.SoftDeletePrescriptionUseCase;
import com.pillmate.prescription.application.UpdatePrescriptionMemoUseCase;
import com.pillmate.prescription.presentation.dto.ResolveCandidateRequest;
import com.pillmate.prescription.presentation.dto.UpdatePrescriptionMemoRequest;
import com.pillmate.common.security.UserContext;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@RestController
@RequestMapping("/prescriptions")
public class PrescriptionController {

    private final GetUploadUrlUseCase getUploadUrlUseCase;
    private final RegisterPrescriptionService registerPrescriptionService;
    private final ExtractPrescriptionOcrUseCase extractPrescriptionOcrUseCase;
    private final GetUnresolvedCandidatesUseCase getUnresolvedCandidatesUseCase;
    private final ResolveCandidateUseCase resolveCandidateUseCase;
    private final GetPrescriptionsUseCase getPrescriptionsUseCase;
    private final GetPrescriptionDetailUseCase getPrescriptionDetailUseCase;
    private final GetLatestPrescriptionWithInsightUseCase getLatestPrescriptionWithInsightUseCase;
    private final UpdatePrescriptionMemoUseCase updatePrescriptionMemoUseCase;
    private final SoftDeletePrescriptionUseCase softDeletePrescriptionUseCase;
    private final Executor ocrExecutor;

    public PrescriptionController(
            GetUploadUrlUseCase getUploadUrlUseCase,
            RegisterPrescriptionService registerPrescriptionService,
            ExtractPrescriptionOcrUseCase extractPrescriptionOcrUseCase,
            GetUnresolvedCandidatesUseCase getUnresolvedCandidatesUseCase,
            ResolveCandidateUseCase resolveCandidateUseCase,
            GetPrescriptionsUseCase getPrescriptionsUseCase,
            GetPrescriptionDetailUseCase getPrescriptionDetailUseCase,
            GetLatestPrescriptionWithInsightUseCase getLatestPrescriptionWithInsightUseCase,
            UpdatePrescriptionMemoUseCase updatePrescriptionMemoUseCase,
            SoftDeletePrescriptionUseCase softDeletePrescriptionUseCase,
            @Qualifier("ocrExecutor") Executor ocrExecutor) {
        this.getUploadUrlUseCase = getUploadUrlUseCase;
        this.registerPrescriptionService = registerPrescriptionService;
        this.extractPrescriptionOcrUseCase = extractPrescriptionOcrUseCase;
        this.getUnresolvedCandidatesUseCase = getUnresolvedCandidatesUseCase;
        this.resolveCandidateUseCase = resolveCandidateUseCase;
        this.getPrescriptionsUseCase = getPrescriptionsUseCase;
        this.getPrescriptionDetailUseCase = getPrescriptionDetailUseCase;
        this.getLatestPrescriptionWithInsightUseCase = getLatestPrescriptionWithInsightUseCase;
        this.updatePrescriptionMemoUseCase = updatePrescriptionMemoUseCase;
        this.softDeletePrescriptionUseCase = softDeletePrescriptionUseCase;
        this.ocrExecutor = ocrExecutor;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PrescriptionSummary>>> list() {
        return ResponseEntity.ok(ApiResponse.success(getPrescriptionsUseCase.list()));
    }

    @GetMapping("/latest-with-insight")
    public ResponseEntity<ApiResponse<LatestPrescriptionWithInsightResponse>> latestWithInsight() {
        return ResponseEntity.ok(ApiResponse.success(
                getLatestPrescriptionWithInsightUseCase.loadLatestForPatient(UserContext.get())));
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

    @PostMapping("/ocr/extract")
    public CompletableFuture<ResponseEntity<ApiResponse<OcrExtractResponse>>> ocrExtract(
            @Valid @RequestBody OcrExtractRequest request) {
        return CompletableFuture.supplyAsync(
                () -> ResponseEntity.ok(ApiResponse.success(
                        extractPrescriptionOcrUseCase.extract(request.imageKey()))),
                ocrExecutor);
    }

    @GetMapping("/{id}/candidates")
    public ResponseEntity<ApiResponse<List<UnresolvedCandidateDto>>> getCandidates(
            @PathVariable Long id) {
        List<UnresolvedCandidateDto> candidates = getUnresolvedCandidatesUseCase.getUnresolved(id);
        return ResponseEntity.ok(ApiResponse.success(candidates));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> updateMemo(
            @PathVariable Long id,
            @Valid @RequestBody UpdatePrescriptionMemoRequest request) {
        updatePrescriptionMemoUseCase.update(id, request.label(), request.memo(), request.symptom());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        softDeletePrescriptionUseCase.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
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
