package com.pillmate.user.infrastructure.persistence;

import com.pillmate.user.domain.model.UserDailyActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

interface UserDailyActivityJpaRepository extends JpaRepository<UserDailyActivity, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO user_daily_activity (user_id, active_date, created_at)
            VALUES (:userId, :activeDate, now())
            ON CONFLICT (user_id, active_date) DO NOTHING
            """, nativeQuery = true)
    void upsert(@Param("userId") Long userId, @Param("activeDate") LocalDate activeDate);

    @Query("""
            SELECT COUNT(DISTINCT a.userId)
            FROM UserDailyActivity a
            WHERE a.activeDate = :today
            """)
    long countDau(@Param("today") LocalDate today);

    @Query("""
            SELECT COUNT(DISTINCT a.userId)
            FROM UserDailyActivity a
            WHERE a.activeDate >= :since
            """)
    long countMau(@Param("since") LocalDate since);
}
