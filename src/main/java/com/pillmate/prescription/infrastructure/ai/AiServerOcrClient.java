package com.pillmate.prescription.infrastructure.ai;

import com.pillmate.prescription.application.port.OcrPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiServerOcrClient implements OcrPort {

    @Override
    public OcrResult extractFromImage(String imageUrl) {
        // TODO: Implementation for T006c
        return null;
    }
}
