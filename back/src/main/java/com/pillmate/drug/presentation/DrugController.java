package com.pillmate.drug.presentation;

import com.pillmate.common.response.ApiResponse;
import com.pillmate.drug.application.GetDrugDetailUseCase;
import com.pillmate.drug.application.SearchDrugUseCase;
import com.pillmate.drug.application.dto.DrugDetailResponse;
import com.pillmate.drug.application.dto.DrugSearchResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/drugs")
@RequiredArgsConstructor
public class DrugController {

    private final SearchDrugUseCase searchDrugUseCase;
    private final GetDrugDetailUseCase getDrugDetailUseCase;

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<DrugSearchResult>>> search(@RequestParam String q) {
        return ResponseEntity.ok(ApiResponse.success(searchDrugUseCase.search(q)));
    }

    @GetMapping("/{kdCode}")
    public ResponseEntity<ApiResponse<DrugDetailResponse>> getDetail(@PathVariable String kdCode) {
        return ResponseEntity.ok(ApiResponse.success(getDrugDetailUseCase.getDetail(kdCode)));
    }
}
