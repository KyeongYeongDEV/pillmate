package com.pillmate.drug.application.port;

import java.util.Optional;

public interface DrugImageUrlCachePort {

    Optional<String> get(String imageS3Key);

    void put(String imageS3Key, String url);
}
