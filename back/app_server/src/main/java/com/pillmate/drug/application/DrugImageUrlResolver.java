package com.pillmate.drug.application;

import com.pillmate.drug.application.port.DrugImageStoragePort;
import com.pillmate.drug.domain.model.Drug;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class DrugImageUrlResolver {

    private static final Duration IMAGE_URL_TTL = Duration.ofHours(1);

    private final DrugImageStoragePort drugImageStoragePort;

    public String resolve(Drug drug) {
        if (drug.hasS3CachedImage()) {
            return drugImageStoragePort.issueViewUrl(drug.getImageS3Key(), IMAGE_URL_TTL);
        }
        return drug.getItemImage();
    }
}
