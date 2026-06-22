package com.pillmate.prescription.application;

import com.pillmate.prescription.application.dto.ExtractedDrugItem;
import com.pillmate.prescription.application.dto.OcrExtractResponse;
import com.pillmate.prescription.application.port.OcrPort.OcrItem;
import com.pillmate.prescription.application.port.OcrPort.OcrResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ExtractPrescriptionOcrUseCase {

    private final OcrExtractService ocrExtractService;

    public OcrExtractResponse extract(String imageKey) {
        OcrResult result = ocrExtractService.extractAndValidate(imageKey);
        List<ExtractedDrugItem> items = result.items().stream()
                .map(this::toExtractedDrugItem)
                .toList();
        return new OcrExtractResponse(items);
    }

    private ExtractedDrugItem toExtractedDrugItem(OcrItem item) {
        return new ExtractedDrugItem(
                item.kdCode(),
                item.nameRaw(),
                item.doseAmount(),
                item.doseUnit(),
                item.frequency(),
                item.durationDays(),
                item.confidence()
        );
    }
}
