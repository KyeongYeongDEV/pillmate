package com.pillmate.prescription.domain.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "prescriptions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Prescription {

    private static final BigDecimal OCR_MIN_CONFIDENCE = new BigDecimal("0.7");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = true)
    private Long careGroupId;

    @Column(nullable = false)
    private Long patientId;

    private String imageKey;

    @Column(nullable = false)
    private LocalDate prescribedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OcrStatus ocrStatus;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "prescription", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PrescribedDrug> drugs = new ArrayList<>();

    @OneToMany(mappedBy = "prescription", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PrescribedDrugCandidate> candidates = new ArrayList<>();

    public static Prescription create(Long patientId, String imageKey, LocalDate prescribedAt) {
        Prescription p = new Prescription();
        p.patientId = patientId;
        p.imageKey = imageKey;
        p.prescribedAt = prescribedAt;
        p.ocrStatus = OcrStatus.PENDING;
        p.createdAt = Instant.now();
        return p;
    }

    public List<PrescribedDrug> getDrugs() {
        return Collections.unmodifiableList(drugs);
    }

    public List<PrescribedDrugCandidate> getCandidates() {
        return Collections.unmodifiableList(candidates);
    }

    public void attachCandidates(List<PrescribedDrugCandidate> newCandidates) {
        for (PrescribedDrugCandidate c : newCandidates) {
            c.assignTo(this);
            candidates.add(c);
        }
    }

    public void updateImageKey(String imageKey) {
        this.imageKey = imageKey;
    }

    public void startOcr() {
        this.ocrStatus = OcrStatus.PROCESSING;
    }

    public void addDrug(PrescribedDrug drug) {
        drug.assignTo(this);
        drugs.add(drug);
    }

    public void markOcrDone() {
        if (hasUnmatchedDrug() || hasLowConfidenceDrug() || hasUnresolvedCandidate()) {
            this.ocrStatus = OcrStatus.MANUAL;
            return;
        }
        this.ocrStatus = OcrStatus.DONE;
    }

    private boolean hasUnmatchedDrug() {
        return drugs.stream().anyMatch(drug -> !drug.isMatched());
    }

    public void markOcrFailed() {
        this.ocrStatus = OcrStatus.FAILED;
    }

    public void markManualReview() {
        this.ocrStatus = OcrStatus.MANUAL;
    }

    private boolean hasLowConfidenceDrug() {
        return drugs.stream().anyMatch(this::isBelowMinConfidence);
    }

    private boolean hasUnresolvedCandidate() {
        return candidates.stream().anyMatch(c -> !c.isResolved());
    }

    private boolean isBelowMinConfidence(PrescribedDrug drug) {
        BigDecimal confidence = drug.getConfidence();
        return confidence != null && confidence.compareTo(OCR_MIN_CONFIDENCE) < 0;
    }
}
