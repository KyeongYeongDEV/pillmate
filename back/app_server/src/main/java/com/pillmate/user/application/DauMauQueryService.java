package com.pillmate.user.application;

import com.pillmate.user.domain.repository.UserDailyActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class DauMauQueryService {

    private final UserDailyActivityRepository userDailyActivityRepository;

    @Transactional(readOnly = true)
    public long getDau() {
        return userDailyActivityRepository.countDau(LocalDate.now());
    }

    @Transactional(readOnly = true)
    public long getMau() {
        return userDailyActivityRepository.countMau(LocalDate.now().minusDays(29));
    }
}
