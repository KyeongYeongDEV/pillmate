package com.pillmate.prescription.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "prescribed_drug_candidates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PrescribedDrugCandidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prescription_id", nullable = false)
    private Prescription prescription;

    @Column(name = "item_index", nullable = false)
    private int itemIndex;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision_type", nullable = false, length = 10)
    private CandidateDecisionType decisionType;

    @Column(nullable = false, length = 30)
    private String reason;

    @Column(name = "options_json", nullable = false, columnDefinition = "jsonb")
    private String optionsJson;

    @Column(name = "resolved_drug_id")
    private Long resolvedDrugId;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by")
    private Long resolvedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static PrescribedDrugCandidate create(int itemIndex, CandidateDecisionType decisionType,
                                                  String reason, String optionsJson) {
        PrescribedDrugCandidate c = new PrescribedDrugCandidate();
        c.itemIndex = itemIndex;
        c.decisionType = decisionType;
        c.reason = reason;
        c.optionsJson = optionsJson;
        c.createdAt = Instant.now();
        return c;
    }

    void assignTo(Prescription prescription) {
        this.prescription = prescription;
    }

    public boolean isResolved() {
        return resolvedDrugId != null;
    }

    public void resolve(Long drugId, Long resolverId) {
        this.resolvedDrugId = drugId;
        this.resolvedAt = Instant.now();
        this.resolvedBy = resolverId;
    }
}
