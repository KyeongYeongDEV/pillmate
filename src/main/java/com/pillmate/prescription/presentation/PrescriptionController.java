package com.pillmate.prescription.presentation;

import com.pillmate.common.response.ApiResponse;
import com.pillmate.common.security.UserContext;
import com.pillmate.prescription.application.GetUploadUrlUseCase;
import com.pillmate.prescription.application.dto.UploadUrlResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/prescriptions")
@RequiredArgsConstructor
public class PrescriptionController {

    private final GetUploadUrlUseCase getUploadUrlUseCase;

    @PostMapping("/upload-url")
    public ResponseEntity<ApiResponse<UploadUrlResponse>> getUploadUrl(
            @RequestParam Long careGroupId,
            @RequestParam Long patientId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate prescribedAt) {
        UserContext.get(); // 인증 확인
        return ResponseEntity.ok(ApiResponse.success(
                getUploadUrlUseCase.getUploadUrl(careGroupId, patientId, prescribedAt)));
    }
}
