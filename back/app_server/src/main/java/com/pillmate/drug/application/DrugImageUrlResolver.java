package com.pillmate.drug.application;

import com.pillmate.drug.application.port.DrugImageStoragePort;
import com.pillmate.drug.application.port.DrugImageUrlCachePort;
import com.pillmate.drug.domain.model.Drug;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class DrugImageUrlResolver {

    // 공개(식약처) 약 이미지 — presign 을 길게 유지해 URL 고정 → 기기 캐시 HIT (캐시 TTL 은 이보다 짧은 23h)
    private static final Duration IMAGE_URL_TTL = Duration.ofHours(24);

    private final DrugImageStoragePort drugImageStoragePort;
    private final DrugImageUrlCachePort drugImageUrlCachePort;

    public String resolve(Drug drug) {
        if (!drug.hasS3CachedImage()) {
            return drug.getItemImage();
        }
        String imageS3Key = drug.getImageS3Key();
        return drugImageUrlCachePort.get(imageS3Key)
                .orElseGet(() -> issueAndCache(imageS3Key));
    }

    private String issueAndCache(String imageS3Key) {
        String url = drugImageStoragePort.issueViewUrl(imageS3Key, IMAGE_URL_TTL);
        drugImageUrlCachePort.put(imageS3Key, url);
        return url;
    }
}
