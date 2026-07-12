package com.pillmate.drug.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "drugs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Drug {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kd_code", unique = true, nullable = false, length = 20)
    private String kdCode;

    @Column(nullable = false, length = 500)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String ingredient;

    // 식약처 원천 성분 컬럼(79% 적재) — ingredient 컬럼은 거의 비어 상세 응답에서 우선 사용
    @Column(name = "main_ingr", columnDefinition = "TEXT")
    private String mainIngr;

    // 약효 분류명(99.7% 적재). DB 실제 컬럼은 VARCHAR(100) — ddl-auto:validate 정합
    @Column(name = "class_name", length = 100)
    private String className;

    @Column(columnDefinition = "TEXT")
    private String efficacy;

    @Column(columnDefinition = "TEXT")
    private String dosage;

    @Column(name = "side_effect", columnDefinition = "TEXT")
    private String sideEffect;

    @Column(length = 50)
    private String form;

    @Column(length = 100)
    private String company;

    @Column(name = "item_image", columnDefinition = "TEXT")
    private String itemImage;

    @Column(name = "image_s3_key", length = 255)
    private String imageS3Key;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DrugStatus status;

    @Column(nullable = false, length = 50)
    private String source;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    @Column(nullable = false)
    private Integer version;

    public static Drug of(String kdCode, String name, String ingredient, String efficacy, String source) {
        Drug drug = new Drug();
        drug.kdCode = kdCode;
        drug.name = name;
        drug.ingredient = ingredient;
        drug.efficacy = efficacy;
        drug.status = DrugStatus.ACTIVE;
        drug.source = source;
        drug.syncedAt = Instant.now();
        drug.version = 1;
        return drug;
    }

    public boolean isActive() {
        return this.status == DrugStatus.ACTIVE;
    }

    public boolean hasS3CachedImage() {
        return imageS3Key != null && !imageS3Key.isBlank();
    }

    public void revoke() {
        this.status = DrugStatus.REVOKED;
    }
}
