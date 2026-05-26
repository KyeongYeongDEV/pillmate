package com.pillmate.prescription.application.port;

import java.math.BigDecimal;
import java.util.List;

public interface OcrPort {
    OcrResult extractFromImage(String imageUrl);

    record OcrResult(List<OcrItem> items, String source) {}

    record OcrItem(
            String kdCode,
            String nameRaw,
            String matchedName,
            BigDecimal doseAmount,
            String doseUnit,
            Integer frequency,
            Integer durationDays,
            BigDecimal confidence,
            String imageUrl,
            String decision,
            String candidateOptionsJson
    ) {
        public OcrItem(String kdCode, String nameRaw, String matchedName, BigDecimal doseAmount,
                       String doseUnit, Integer frequency, Integer durationDays,
                       BigDecimal confidence, String imageUrl) {
            this(kdCode, nameRaw, matchedName, doseAmount, doseUnit, frequency, durationDays,
                    confidence, imageUrl, "AUTO", null);
        }
    }
}
