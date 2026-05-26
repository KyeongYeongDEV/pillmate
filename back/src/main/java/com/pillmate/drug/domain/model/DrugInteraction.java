package com.pillmate.drug.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "drug_interactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DrugInteraction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "drug_code_a", nullable = false, length = 20)
    private String drugCodeA;

    @Column(name = "drug_code_b", nullable = false, length = 20)
    private String drugCodeB;

    @Column(nullable = false, length = 30)
    private String type;

    @Column(nullable = false, length = 20)
    private String severity;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 50)
    private String source;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    public boolean isCritical() {
        return "CRITICAL".equals(this.severity);
    }
}
