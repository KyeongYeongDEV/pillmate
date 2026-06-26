package com.pillmate.user.application;

import com.pillmate.user.domain.repository.UserDailyActivityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserActivityRecordingService {

    private final UserDailyActivityRepository userDailyActivityRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long userId) {
        try {
            userDailyActivityRepository.upsert(userId, LocalDate.now());
        } catch (Exception e) {
            log.warn("DAU 활동 기록 실패 (best-effort, 무시): userId={}", userId, e);
        }
    }
}
