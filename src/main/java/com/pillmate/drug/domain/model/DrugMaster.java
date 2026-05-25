package com.pillmate.drug.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "drug_master")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DrugMaster {

    @Id
    @Column(name = "item_seq", length = 20)
    private String itemSeq;

    @Column(name = "product_name", nullable = false, length = 500)
    private String productName;

    @Column(name = "ingredient_code", length = 100)
    private String ingredientCode;

    @Column(name = "ingredient_name", length = 500)
    private String ingredientName;

    @Column(name = "dose_amount", precision = 10, scale = 3)
    private BigDecimal doseAmount;

    @Column(name = "dose_unit", length = 20)
    private String doseUnit;

    @Column(name = "form", length = 50)
    private String form;

    @Column(name = "company", length = 200)
    private String company;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "source", length = 50)
    private String source;

    @Column(name = "synced_at")
    private Instant syncedAt;

    @Column(name = "legacy_drug_id")
    private Long legacyDrugId;

    public static DrugMaster create(
            String itemSeq, String productName,
            String ingredientCode, String ingredientName,
            BigDecimal doseAmount, String doseUnit,
            String form, String company,
            String imageUrl, String source, Long legacyDrugId) {
        DrugMaster m = new DrugMaster();
        m.itemSeq = itemSeq;
        m.productName = productName;
        m.ingredientCode = ingredientCode;
        m.ingredientName = ingredientName;
        m.doseAmount = doseAmount;
        m.doseUnit = doseUnit;
        m.form = form;
        m.company = company;
        m.imageUrl = imageUrl;
        m.source = source;
        m.syncedAt = Instant.now();
        m.legacyDrugId = legacyDrugId;
        return m;
    }
}
