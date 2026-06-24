package com.pillmate.prescription.application;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.prescription.application.port.FileStoragePort;
import com.pillmate.prescription.application.port.OcrPort;
import com.pillmate.prescription.application.port.OcrPort.OcrResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class OcrExtractService {

    private static final Duration OCR_DOWNLOAD_TTL = Duration.ofMinutes(10);

    private final FileStoragePort fileStoragePort;
    private final OcrPort ocrPort;

    public OcrResult extractAndValidate(String imageKey) {
        String downloadUrl = fileStoragePort.issueDownloadUrl(imageKey, OCR_DOWNLOAD_TTL);
        OcrResult result = ocrPort.extractFromImage(downloadUrl, imageKey);
        requireNonEmpty(result);
        return result;
    }

    private void requireNonEmpty(OcrResult result) {
        if (result.items().isEmpty()) {
            throw new PillmateException(ErrorCode.OCR_EMPTY);
        }
    }
}
