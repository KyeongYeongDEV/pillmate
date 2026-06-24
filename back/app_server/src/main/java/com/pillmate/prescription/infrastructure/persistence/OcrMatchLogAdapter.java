package com.pillmate.prescription.infrastructure.persistence;

import com.pillmate.prescription.application.port.OcrMatchLogPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OcrMatchLogAdapter implements OcrMatchLogPort {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void updateUserCorrection(String imageKey, String rawOcrText, String kdCode) {
        try {
            jdbcTemplate.update(
                "UPDATE ocr_match_logs SET user_corrected_kd_code = ? " +
                "WHERE id = (SELECT id FROM ocr_match_logs " +
                "WHERE image_key = ? AND raw_ocr_text = ? AND user_corrected_kd_code IS NULL " +
                "ORDER BY created_at DESC LIMIT 1)",
                kdCode, imageKey, rawOcrText);
        } catch (Exception e) {
            log.warn("ocr_match_log update skipped (best-effort): {}", e.getMessage());
        }
    }
}
