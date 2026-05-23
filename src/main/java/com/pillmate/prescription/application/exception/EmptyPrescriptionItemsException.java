package com.pillmate.prescription.application.exception;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;

public class EmptyPrescriptionItemsException extends PillmateException {

    public EmptyPrescriptionItemsException() {
        super(ErrorCode.PRESCRIPTION_ITEMS_EMPTY);
    }
}
