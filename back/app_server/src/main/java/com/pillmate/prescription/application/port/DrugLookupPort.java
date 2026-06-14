package com.pillmate.prescription.application.port;

import java.util.Optional;

public interface DrugLookupPort {

    Optional<DrugSummary> findByKdCode(String kdCode);

    Optional<DrugSummary> findById(Long drugId);

    record DrugSummary(Long drugId, String kdCode, String name, String imageUrl) {}
}
