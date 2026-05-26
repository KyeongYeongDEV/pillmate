package com.pillmate.prescription.application.port;

import java.util.List;

public interface DrugInteractionPort {

    List<DrugInteractionRecord> findByKdCodes(List<String> kdCodes);

    record DrugInteractionRecord(
            String drugCodeA,
            String drugCodeB,
            String severity,
            String description,
            String source
    ) {}
}
