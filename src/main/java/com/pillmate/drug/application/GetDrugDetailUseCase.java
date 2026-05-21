package com.pillmate.drug.application;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.drug.application.dto.DrugDetailResponse;
import com.pillmate.drug.application.port.DrugCachePort;
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

    public DrugDetailResponse getDetail(Long drugId) {
        return drugCachePort.get(drugId).orElseGet(() -> {
            DrugDetailResponse response = drugRepository.findById(drugId)
                    .map(DrugDetailResponse::from)
                    .orElseThrow(() -> new PillmateException(ErrorCode.DRUG_NOT_FOUND));
            drugCachePort.put(drugId, response);
            return response;
        });
    }
}
