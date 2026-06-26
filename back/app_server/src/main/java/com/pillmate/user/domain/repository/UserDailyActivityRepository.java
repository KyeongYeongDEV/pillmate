package com.pillmate.user.domain.repository;

import java.time.LocalDate;

public interface UserDailyActivityRepository {
    void upsert(Long userId, LocalDate activeDate);
    long countDau(LocalDate today);
    long countMau(LocalDate since);
}
