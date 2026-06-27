package com.pillmate.prescription.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "prescription_insights")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PrescriptionInsight {

    private static final BigDecimal MIN_CONFIDENCE = new BigDecimal("0.7");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "prescription_id", nullable = false)
    private Long prescriptionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PrescriptionInsightType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PrescriptionInsightSeverity severity;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 200)
    private String source;

    @Column(nullable = false, precision = 4, scale = 3)
    private BigDecimal confidence;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private PrescriptionInsight(Long prescriptionId, PrescriptionInsightType type,
                               PrescriptionInsightSeverity severity, String title,
                               String description, String source, BigDecimal confidence) {
        this.prescriptionId = prescriptionId;
        this.type = type;
        this.severity = severity;
        this.title = title;
        this.description = description;
        this.source = source;
        this.confidence = confidence;
        this.createdAt = Instant.now();
    }

    public static PrescriptionInsight create(Long prescriptionId, PrescriptionInsightType type,
                                             PrescriptionInsightSeverity severity, String title,
                                             String description, String source, BigDecimal confidence) {
        requireSource(source);
        requireMinConfidence(confidence);
        return new PrescriptionInsight(prescriptionId, type, severity, title, description, source, confidence);
    }

    private static void requireSource(String source) {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("insight source must not be blank");
        }
    }

    private static void requireMinConfidence(BigDecimal confidence) {
        if (confidence == null || confidence.compareTo(MIN_CONFIDENCE) < 0) {
            throw new IllegalArgumentException("insight confidence must be >= 0.7");
        }
    }
}
