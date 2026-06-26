package com.pillmate.user.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "user_daily_activity",
       uniqueConstraints = @UniqueConstraint(
               name = "uq_user_daily_activity",
               columnNames = {"user_id", "active_date"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserDailyActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private LocalDate activeDate;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public static UserDailyActivity of(Long userId, LocalDate activeDate) {
        UserDailyActivity entity = new UserDailyActivity();
        entity.userId = userId;
        entity.activeDate = activeDate;
        entity.createdAt = Instant.now();
        return entity;
    }
}
