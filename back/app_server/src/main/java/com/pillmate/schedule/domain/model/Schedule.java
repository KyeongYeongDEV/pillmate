package com.pillmate.schedule.domain.model;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "schedules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Schedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long careGroupId;

    @Column(nullable = false)
    private Long patientId;

    @Column(name = "drug_id")
    private Long drugId;

    @Column(name = "prescription_id")
    private Long prescriptionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TimeOfDay timeOfDay;

    @Column(name = "custom_time", nullable = false)
    private LocalTime customTime;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private Long createdBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public static Schedule of(Long careGroupId, Long patientId, Long drugId,
                              TimeOfDay timeOfDay, LocalDate startDate, LocalDate endDate,
                              Long createdBy) {
        return of(careGroupId, patientId, drugId, timeOfDay, null, startDate, endDate, createdBy);
    }

    public static Schedule of(Long careGroupId, Long patientId, Long drugId,
                              TimeOfDay timeOfDay, LocalTime customTime,
                              LocalDate startDate, LocalDate endDate, Long createdBy) {
        return of(careGroupId, patientId, drugId, null, timeOfDay, customTime, startDate, endDate, createdBy);
    }

    public static Schedule forPrescription(Long careGroupId, Long patientId, Long prescriptionId,
                                           TimeOfDay timeOfDay, LocalTime customTime,
                                           LocalDate startDate, LocalDate endDate, Long createdBy) {
        return of(careGroupId, patientId, null, prescriptionId, timeOfDay, customTime, startDate, endDate, createdBy);
    }

    public static Schedule of(Long careGroupId, Long patientId, Long drugId, Long prescriptionId,
                              TimeOfDay timeOfDay, LocalTime customTime,
                              LocalDate startDate, LocalDate endDate, Long createdBy) {
        Schedule s = new Schedule();
        s.careGroupId = careGroupId;
        s.patientId = patientId;
        s.drugId = drugId;
        s.prescriptionId = prescriptionId;
        s.timeOfDay = timeOfDay;
        s.customTime = customTime != null ? customTime : timeOfDay.defaultTime();
        s.startDate = startDate;
        s.endDate = endDate;
        s.active = true;
        s.createdBy = createdBy;
        s.createdAt = Instant.now();
        return s;
    }

    public boolean overlapsWith(LocalDate otherStart, LocalDate otherEnd) {
        // endDate null = 무기한 스케줄 — 어느 방향이든 무기한이면 그 경계는 항상 겹침
        boolean startsBeforeOtherEnds = otherEnd == null || !startDate.isAfter(otherEnd);
        boolean endsAfterOtherStarts = endDate == null || !endDate.isBefore(otherStart);
        return startsBeforeOtherEnds && endsAfterOtherStarts;
    }

    public void deactivate() {
        this.active = false;
    }

    public void updateTimeOfDay(TimeOfDay newTimeOfDay) {
        this.timeOfDay = newTimeOfDay;
    }

    public void updateEndDate(LocalDate newEndDate) {
        this.endDate = newEndDate;
    }

    public void changeTime(LocalTime newTime, LocalDate today) {
        requireWithinPeriod(today);
        this.customTime = newTime;
    }

    private void requireWithinPeriod(LocalDate today) {
        if (endDate != null && today.isAfter(endDate)) {
            throw new PillmateException(ErrorCode.SCHEDULE_PERIOD_ENDED);
        }
    }
}
