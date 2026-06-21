package com.pillmate.common.prescription;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public final class PrescriptionLabel {

    private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("M월d일");

    private PrescriptionLabel() {
    }

    public static String of(LocalDate prescribedAt, String leadDrugName, int drugCount) {
        String datePart = prescribedAt != null ? prescribedAt.format(MONTH_DAY) : "처방전";
        if (drugCount <= 0 || isBlank(leadDrugName)) {
            return datePart + " 처방전";
        }
        if (drugCount == 1) {
            return datePart + "·" + leadDrugName;
        }
        return datePart + "·" + leadDrugName + " 외" + (drugCount - 1) + "종";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
