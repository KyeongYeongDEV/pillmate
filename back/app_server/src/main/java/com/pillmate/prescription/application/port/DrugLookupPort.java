package com.pillmate.prescription.application.port;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface DrugLookupPort {

    Optional<DrugSummary> findByKdCode(String kdCode);

    Optional<DrugSummary> findById(Long drugId);

    Map<Long, DrugSummary> findByIds(Collection<Long> drugIds);

    record DrugSummary(Long drugId, String kdCode, String name, String imageUrl) {}
}
