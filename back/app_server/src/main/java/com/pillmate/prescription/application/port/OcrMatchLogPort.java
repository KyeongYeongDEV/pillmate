package com.pillmate.prescription.application.port;

public interface OcrMatchLogPort {
    void updateUserCorrection(String imageKey, String rawOcrText, String kdCode);
}
