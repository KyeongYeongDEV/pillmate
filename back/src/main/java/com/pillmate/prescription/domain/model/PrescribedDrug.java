package com.pillmate.prescription.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "prescribed_drugs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PrescribedDrug {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prescription_id", nullable = false)
    private Prescription prescription;

    @Column(name = "drug_id")
    private Long drugId;

    @Column(name = "name_raw", length = 200, nullable = false)
    private String nameRaw;

    @Column(name = "dose_amount", precision = 8, scale = 2)
    private BigDecimal doseAmount;

    @Column(name = "dose_unit", length = 20)
    private String doseUnit;

    @Column(nullable = false)
    private Integer frequency;

    @Column(name = "duration_days")
    private Integer durationDays;

    @Column(precision = 4, scale = 3)
    private BigDecimal confidence;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Builder
    private PrescribedDrug(
            Long drugId,
            String nameRaw,
            BigDecimal doseAmount,
            String doseUnit,
            Integer frequency,
            Integer durationDays,
            BigDecimal confidence
    ) {
        this.drugId = drugId;
        this.nameRaw = nameRaw;
        this.doseAmount = doseAmount;
        this.doseUnit = doseUnit;
        this.frequency = frequency == null ? 3 : frequency;
        this.durationDays = durationDays;
        this.confidence = confidence;
        this.createdAt = Instant.now();
    }

    void assignTo(Prescription prescription) {
        this.prescription = prescription;
    }

    public boolean isMatched() {
        return drugId != null;
    }
}
