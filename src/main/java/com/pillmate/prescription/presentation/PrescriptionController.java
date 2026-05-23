package com.pillmate.prescription.presentation;

import com.pillmate.common.response.ApiResponse;
import com.pillmate.prescription.application.GetUploadUrlUseCase;
import com.pillmate.prescription.application.OcrAndRegisterPrescriptionUseCase;
import com.pillmate.prescription.application.RegisterPrescriptionService;
import com.pillmate.prescription.application.dto.RegisterPrescriptionResponse;
import com.pillmate.prescription.application.dto.UploadUrlResponse;
import com.pillmate.prescription.presentation.dto.OcrRegisterRequest;
import com.pillmate.prescription.presentation.dto.RegisterPrescriptionRequest;
import com.pillmate.prescription.presentation.dto.UploadUrlRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/prescriptions")
@RequiredArgsConstructor
public class PrescriptionController {

    private final GetUploadUrlUseCase getUploadUrlUseCase;
    private final RegisterPrescriptionService registerPrescriptionService;
    private final OcrAndRegisterPrescriptionUseCase ocrAndRegisterPrescriptionUseCase;

    @PostMapping("/upload-url")
    public ResponseEntity<ApiResponse<UploadUrlResponse>> issueUploadUrl(
            @Valid @RequestBody UploadUrlRequest request) {
        UploadUrlResponse response = getUploadUrlUseCase.issue(request.careGroupId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RegisterPrescriptionResponse>> register(
            @Valid @RequestBody RegisterPrescriptionRequest request) {
        RegisterPrescriptionResponse response = registerPrescriptionService.register(request.toCommand());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/ocr")
    public ResponseEntity<ApiResponse<RegisterPrescriptionResponse>> ocrRegister(
            @Valid @RequestBody OcrRegisterRequest request) {
        RegisterPrescriptionResponse response = ocrAndRegisterPrescriptionUseCase.ocrAndRegister(
                request.careGroupId(), request.patientId(),
                request.prescribedAt(), request.imageKey());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
