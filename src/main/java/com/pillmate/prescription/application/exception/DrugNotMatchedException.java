package com.pillmate.prescription.application.exception;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;

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
