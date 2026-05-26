package com.pillmate.drug.presentation;

import com.pillmate.common.response.ApiResponse;
import com.pillmate.drug.application.ListPendingAliasesUseCase;
import com.pillmate.drug.application.RegisterUserAliasUseCase;
import com.pillmate.drug.application.VerifyAliasUseCase;
import com.pillmate.drug.application.dto.PendingAliasItem;
import com.pillmate.drug.application.dto.RegisterUserAliasCommand;
import com.pillmate.drug.application.dto.RegisterUserAliasResponse;
import com.pillmate.drug.application.dto.VerifyAliasResponse;
import com.pillmate.drug.presentation.dto.RegisterAliasRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/drugs/aliases")
@RequiredArgsConstructor
public class DrugAliasController {

    private final RegisterUserAliasUseCase registerUserAliasUseCase;
    private final ListPendingAliasesUseCase listPendingAliasesUseCase;
    private final VerifyAliasUseCase verifyAliasUseCase;

    @PostMapping
    public ResponseEntity<ApiResponse<RegisterUserAliasResponse>> registerAlias(
            @Valid @RequestBody RegisterAliasRequest request) {
        RegisterUserAliasCommand cmd = new RegisterUserAliasCommand(request.nameRaw(), request.itemSeq());
        return ResponseEntity.ok(ApiResponse.success(registerUserAliasUseCase.register(cmd)));
    }

    @GetMapping("/pending-review")
    public ResponseEntity<ApiResponse<Page<PendingAliasItem>>> listPendingReview(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(listPendingAliasesUseCase.listPending(pageable)));
    }

    @PatchMapping("/{id}/verify")
    public ResponseEntity<ApiResponse<VerifyAliasResponse>> verifyAlias(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(verifyAliasUseCase.verify(id)));
    }
}
