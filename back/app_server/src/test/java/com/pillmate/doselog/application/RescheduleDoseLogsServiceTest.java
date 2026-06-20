package com.pillmate.doselog.application;

import com.pillmate.doselog.domain.model.DoseLog;
import com.pillmate.doselog.domain.model.DoseStatus;
import com.pillmate.doselog.domain.repository.DoseLogRepository;
import com.pillmate.doselog.domain.service.DoseLogSchedulePolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("RescheduleDoseLogsService — 스케줄 시각 변경 시 오늘/미래 PENDING dose_log 재예약")
@ExtendWith(MockitoExtension.class)
class RescheduleDoseLogsServiceTest {

    @Mock DoseLogRepository doseLogRepository;

    private final DoseLogSchedulePolicy policy = new DoseLogSchedulePolicy();

    private static final Long SCHEDULE_ID = 1L;

    private RescheduleDoseLogsService sut() {
        return new RescheduleDoseLogsService(doseLogRepository, policy);
    }

    @Test
    @DisplayName("오늘 PENDING dose_log → 같은 날 새 시각(KST 09:30=UTC 00:30)으로 재예약 + 저장")
    void reschedule_pendingToday_movesToNewTimeAndSaves() {
        // given — 오늘(KST 2026-05-28) 08:00 PENDING dose_log
        DoseLog pending = DoseLog.of(SCHEDULE_ID, 2L, Instant.parse("2026-05-27T23:00:00Z"));
        given(doseLogRepository.findByScheduleIdAndStatusFrom(eq(SCHEDULE_ID), eq(DoseStatus.PENDING), any()))
                .willReturn(List.of(pending));

        // when
        sut().rescheduleFuturePending(SCHEDULE_ID, LocalTime.of(9, 30), LocalDate.of(2026, 5, 28));

        // then — 같은 날 09:30 KST = UTC 00:30 으로 재예약
        assertThat(pending.getScheduledAt()).isEqualTo(Instant.parse("2026-05-28T00:30:00Z"));
        verify(doseLogRepository).save(pending);
    }

    @Test
    @DisplayName("fromDate 기준 startOfDay(KST 자정=전일 15:00Z)부터 조회한다")
    void reschedule_queriesFromStartOfDay() {
        // given
        given(doseLogRepository.findByScheduleIdAndStatusFrom(any(), any(), any()))
                .willReturn(List.of());

        // when
        sut().rescheduleFuturePending(SCHEDULE_ID, LocalTime.of(9, 30), LocalDate.of(2026, 5, 28));

        // then — KST 2026-05-28 00:00 = UTC 2026-05-27 15:00
        verify(doseLogRepository).findByScheduleIdAndStatusFrom(
                SCHEDULE_ID, DoseStatus.PENDING, Instant.parse("2026-05-27T15:00:00Z"));
        verify(doseLogRepository, never()).save(any());
    }
}
