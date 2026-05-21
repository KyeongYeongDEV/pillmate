package com.pillmate.prescription.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "prescriptions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Prescription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
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

    public static Prescription create(Long careGroupId, Long patientId,
                                      String imageKey, LocalDate prescribedAt) {
        Prescription p = new Prescription();
        p.careGroupId = careGroupId;
        p.patientId = patientId;
        p.imageKey = imageKey;
        p.prescribedAt = prescribedAt;
        p.ocrStatus = OcrStatus.PENDING;
        p.createdAt = Instant.now();
        return p;
    }

    public void updateImageKey(String imageKey) {
        this.imageKey = imageKey;
    }

    public void startOcr() {
        this.ocrStatus = OcrStatus.PROCESSING;
    }
}
