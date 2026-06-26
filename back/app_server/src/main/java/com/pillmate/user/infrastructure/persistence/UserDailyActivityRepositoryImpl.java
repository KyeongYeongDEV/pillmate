package com.pillmate.user.infrastructure.persistence;

import com.pillmate.user.domain.repository.UserDailyActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
@RequiredArgsConstructor
public class UserDailyActivityRepositoryImpl implements UserDailyActivityRepository {

    private final UserDailyActivityJpaRepository jpa;

    @Override
    public void upsert(Long userId, LocalDate activeDate) {
        jpa.upsert(userId, activeDate);
    }

    @Override
    public long countDau(LocalDate today) {
        return jpa.countDau(today);
    }

    @Override
    public long countMau(LocalDate since) {
        return jpa.countMau(since);
    }
}
