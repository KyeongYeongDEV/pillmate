package com.pillmate.prescription.application.port;

import java.util.Optional;

public interface DrugLookupPort {

    Optional<DrugSummary> findByKdCode(String kdCode);

    record DrugSummary(Long drugId, String kdCode, String name) {}
}
