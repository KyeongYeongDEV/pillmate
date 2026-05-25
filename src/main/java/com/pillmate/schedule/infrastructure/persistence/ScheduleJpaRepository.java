package com.pillmate.schedule.infrastructure.persistence;

import com.pillmate.schedule.domain.model.Schedule;
import com.pillmate.schedule.domain.model.TimeOfDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

interface ScheduleJpaRepository extends JpaRepository<Schedule, Long> {

    @Query("SELECT s FROM Schedule s WHERE s.patientId = :patientId AND s.timeOfDay = :timeOfDay AND s.endDate >= :date")
    List<Schedule> findActiveByPatientAndTime(@Param("patientId") Long patientId,
                                              @Param("timeOfDay") TimeOfDay timeOfDay,
                                              @Param("date") LocalDate date);

    List<Schedule> findAllByPatientId(Long patientId);

    List<Schedule> findByPatientIdAndActiveOrderByTimeOfDayAsc(Long patientId, boolean active);
}
