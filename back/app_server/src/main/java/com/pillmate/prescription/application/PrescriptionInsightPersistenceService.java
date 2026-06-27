package com.pillmate.prescription.application;

import com.pillmate.prescription.domain.model.PrescriptionInsight;
import com.pillmate.prescription.domain.repository.PrescriptionInsightRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PrescriptionInsightPersistenceService {

    private final PrescriptionInsightRepository insightRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveAll(List<PrescriptionInsight> insights) {
        insights.forEach(insightRepository::save);
    }
}
