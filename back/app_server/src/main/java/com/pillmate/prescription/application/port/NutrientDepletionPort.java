package com.pillmate.prescription.application.port;

import com.pillmate.prescription.application.dto.NutrientNote;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface NutrientDepletionPort {
    Map<Long, List<NutrientNote>> findByDrugIds(Collection<Long> drugIds);
}
