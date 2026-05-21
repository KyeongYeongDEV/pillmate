package com.pillmate.drug.application.port;

import com.pillmate.drug.application.dto.DrugDetailResponse;

import java.util.Optional;

public interface DrugCachePort {

    Optional<DrugDetailResponse> get(Long drugId);

    void put(Long drugId, DrugDetailResponse response);

    void evict(Long drugId);
}
