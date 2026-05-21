package com.pillmate.schedule.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "schedules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Schedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long careGroupId;

    @Column(nullable = false)
    private Long patientId;

    @Column(nullable = false)
    private Long drugId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TimeOfDay timeOfDay;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private Long createdBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public static Schedule of(Long careGroupId, Long patientId, Long drugId,
                               TimeOfDay timeOfDay, LocalDate startDate, LocalDate endDate,
                               Long createdBy) {
        Schedule s = new Schedule();
        s.careGroupId = careGroupId;
        s.patientId = patientId;
        s.drugId = drugId;
        s.timeOfDay = timeOfDay;
        s.startDate = startDate;
        s.endDate = endDate;
        s.createdBy = createdBy;
        s.createdAt = Instant.now();
        return s;
    }

    public boolean overlapsWith(LocalDate otherStart, LocalDate otherEnd) {
        return !startDate.isAfter(otherEnd) && !endDate.isBefore(otherStart);
    }
}
