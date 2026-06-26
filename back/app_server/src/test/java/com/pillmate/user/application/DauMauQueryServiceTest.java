package com.pillmate.user.application;

import com.pillmate.user.domain.repository.UserDailyActivityRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
@DisplayName("DauMauQueryService — DAU/MAU 집계")
class DauMauQueryServiceTest {

    @Mock UserDailyActivityRepository userDailyActivityRepository;
    @InjectMocks DauMauQueryService dauMauQueryService;

    @Test
    @DisplayName("오늘 활동한 distinct user 수를 DAU로 반환")
    void getDau_returnsDistinctCountForToday() {
        given(userDailyActivityRepository.countDau(LocalDate.now())).willReturn(5L);

        assertThat(dauMauQueryService.getDau()).isEqualTo(5L);
    }

    @Test
    @DisplayName("최근 30일 distinct user 수를 MAU로 반환")
    void getMau_returnsDistinctCountForLast30Days() {
        LocalDate since = LocalDate.now().minusDays(29);
        given(userDailyActivityRepository.countMau(since)).willReturn(42L);

        assertThat(dauMauQueryService.getMau()).isEqualTo(42L);
    }
}
