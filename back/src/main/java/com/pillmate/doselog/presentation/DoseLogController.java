package com.pillmate.doselog.presentation;

import com.pillmate.common.response.ApiResponse;
import com.pillmate.common.security.UserContext;
import com.pillmate.doselog.application.CheckDoseUseCase;
import com.pillmate.doselog.application.GetDoseHistoryUseCase;
import com.pillmate.doselog.application.dto.CheckDoseRequest;
import com.pillmate.doselog.application.dto.DoseLogResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/dose-logs")
@RequiredArgsConstructor
public class DoseLogController {

    private final CheckDoseUseCase checkDoseUseCase;
    private final GetDoseHistoryUseCase getDoseHistoryUseCase;

    @PatchMapping("/check")
    public ResponseEntity<ApiResponse<DoseLogResponse>> check(
            @RequestBody @Valid CheckDoseRequest request) {
        Long userId = UserContext.get();
        return ResponseEntity.ok(ApiResponse.success(checkDoseUseCase.check(request, userId)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<DoseLogResponse>>> history(
            @RequestParam Long patientId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ResponseEntity.ok(ApiResponse.success(
                getDoseHistoryUseCase.getHistory(patientId, from, to)));
    }
}
