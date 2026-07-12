package com.pillmate.schedule.infrastructure.persistence;

import com.pillmate.schedule.domain.model.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

interface ScheduleJpaRepository extends JpaRepository<Schedule, Long> {

    @Query("SELECT s FROM Schedule s WHERE s.patientId = :patientId AND s.active = true AND (s.endDate IS NULL OR s.endDate >= :date)")
    List<Schedule> findActiveByPatient(@Param("patientId") Long patientId,
                                       @Param("date") LocalDate date);

    List<Schedule> findAllByPatientId(Long patientId);

    List<Schedule> findByPatientIdAndActiveOrderByTimeOfDayAsc(Long patientId, boolean active);

    @Query("SELECT s FROM Schedule s WHERE s.active = true AND s.startDate <= :date AND s.endDate >= :date")
    List<Schedule> findAllActiveOn(@Param("date") LocalDate date);

    List<Schedule> findByPrescriptionIdAndActiveTrue(Long prescriptionId);
}
