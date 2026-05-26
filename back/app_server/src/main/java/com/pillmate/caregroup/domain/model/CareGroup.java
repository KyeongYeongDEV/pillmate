package com.pillmate.caregroup.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "care_groups")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CareGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static CareGroup create(String name, Long creatorUserId) {
        CareGroup g = new CareGroup();
        g.name = name;
        g.createdBy = creatorUserId;
        g.createdAt = Instant.now();
        g.updatedAt = Instant.now();
        return g;
    }
}
