package com.pillmate.prescription.application.exception;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;

/**
 * @deprecated T-OCR-FIX3 정책 변경(2026-05-24): 부분 매칭 허용. 더 이상 던지지 않음.
 *             ErrorCode {@code PRESCRIPTION_DRUG_NOT_MATCHED} 와 함께 후속 task 에서 제거 예정.
 */
@Deprecated(forRemoval = true)
public class DrugNotMatchedException extends PillmateException {

    private final String kdCode;

    public DrugNotMatchedException(String kdCode) {
        super(ErrorCode.PRESCRIPTION_DRUG_NOT_MATCHED);
        this.kdCode = kdCode;
    }

    public String getKdCode() {
        return kdCode;
    }
}
