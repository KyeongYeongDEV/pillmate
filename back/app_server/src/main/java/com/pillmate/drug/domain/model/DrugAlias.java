package com.pillmate.drug.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "drug_alias")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DrugAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alias", nullable = false, length = 500)
    private String alias;

    @Column(name = "alias_jamo", length = 1000)
    private String aliasJamo;

    @Column(name = "item_seq", nullable = false, length = 20)
    private String itemSeq;

    @Column(name = "source", nullable = false, length = 20)
    private AliasSource source;

    @Column(name = "confidence", nullable = false)
    private int confidence;

    @Column(name = "is_verified", nullable = false)
    private boolean verified;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static DrugAlias create(
            String alias, String aliasJamo,
            String itemSeq, AliasSource source, int confidence) {
        DrugAlias a = new DrugAlias();
        a.alias = alias;
        a.aliasJamo = aliasJamo;
        a.itemSeq = itemSeq;
        a.source = source;
        a.confidence = confidence;
        a.verified = false;
        a.createdAt = Instant.now();
        return a;
    }

    public void verify() {
        this.verified = true;
        this.confidence = 100;
    }
}
