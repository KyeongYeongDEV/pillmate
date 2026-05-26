package com.pillmate.drug.application;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.drug.application.dto.DrugDetailResponse;
import com.pillmate.drug.application.port.DrugCachePort;
import com.pillmate.drug.domain.model.Drug;
import com.pillmate.drug.domain.repository.DrugRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetDrugDetailUseCase {

    private final DrugRepository drugRepository;
    private final DrugCachePort drugCachePort;
    private final DrugImageUrlResolver drugImageUrlResolver;

    public DrugDetailResponse getDetail(String kdCode) {
        return drugCachePort.get(kdCode)
                .orElseGet(() -> loadFromRepositoryAndCache(kdCode));
    }

    private DrugDetailResponse loadFromRepositoryAndCache(String kdCode) {
        Drug drug = drugRepository.findByKdCode(kdCode)
                .orElseThrow(() -> new PillmateException(ErrorCode.DRUG_NOT_FOUND));
        DrugDetailResponse response = DrugDetailResponse.from(drug, drugImageUrlResolver.resolve(drug));
        drugCachePort.put(kdCode, response);
        return response;
    }
}
