package com.pillmate.drug.application.port;

import com.pillmate.drug.application.dto.DrugDetailResponse;

import java.util.Optional;

public interface DrugCachePort {

    Optional<DrugDetailResponse> get(String kdCode);

    void put(String kdCode, DrugDetailResponse response);

    void evict(String kdCode);
}
