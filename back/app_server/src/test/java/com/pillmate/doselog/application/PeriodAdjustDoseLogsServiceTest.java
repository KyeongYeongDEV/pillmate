package com.pillmate.doselog.application;

import com.pillmate.doselog.domain.model.DoseLog;
import com.pillmate.doselog.domain.model.DoseStatus;
import com.pillmate.doselog.domain.repository.DoseLogRepository;
import com.pillmate.doselog.domain.service.DoseLogSchedulePolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("PeriodAdjustDoseLogsService — 처방 기간 변경 dose_log 조정")
class PeriodAdjustDoseLogsServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Long SCHEDULE_ID = 1L;
    private static final Long PATIENT_ID = 7L;
    private static final LocalTime CUSTOM_TIME = LocalTime.of(8, 0);

    @Mock DoseLogRepository doseLogRepository;

    private PeriodAdjustDoseLogsService sut;
    private DoseLogSchedulePolicy policy;

    @BeforeEach
    void setUp() {
        policy = new DoseLogSchedulePolicy();
        sut = new PeriodAdjustDoseLogsService(doseLogRepository, policy);
    }

    @Test
    @DisplayName("createLogsForRange — 해당 날짜에 로그 없으면 생성")
    void createLogsForRange_noExisting_createsLogs() {
        LocalDate from = LocalDate.of(2026, 7, 1);
        LocalDate to = LocalDate.of(2026, 7, 3);
        given(doseLogRepository.existsByScheduleIdAndScheduledAtInRange(
                eq(SCHEDULE_ID), any(), any())).willReturn(false);

        int created = sut.createLogsForRange(SCHEDULE_ID, PATIENT_ID, CUSTOM_TIME, from, to);

        assertThat(created).isEqualTo(3);
        ArgumentCaptor<DoseLog> captor = ArgumentCaptor.forClass(DoseLog.class);
        verify(doseLogRepository, org.mockito.Mockito.times(3)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(DoseLog::getStatus)
                .containsOnly(DoseStatus.PENDING);
    }

    @Test
    @DisplayName("createLogsForRange — 이미 로그 있는 날은 skip (중복 방지)")
    void createLogsForRange_alreadyExists_skips() {
        LocalDate from = LocalDate.of(2026, 7, 1);
        LocalDate to = LocalDate.of(2026, 7, 1);
        given(doseLogRepository.existsByScheduleIdAndScheduledAtInRange(
                eq(SCHEDULE_ID), any(), any())).willReturn(true);

        int created = sut.createLogsForRange(SCHEDULE_ID, PATIENT_ID, CUSTOM_TIME, from, to);

        assertThat(created).isEqualTo(0);
        verify(doseLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("skipPendingAfter — cutoffDate 이후 PENDING 로그를 SKIPPED(기간 변경) 처리")
    void skipPendingAfter_marksPendingAsSkipped() {
        LocalDate cutoff = LocalDate.of(2026, 6, 30);
        Instant afterCutoff = policy.startOfNextDay(cutoff);
        DoseLog pendingLog = DoseLog.of(SCHEDULE_ID, PATIENT_ID, afterCutoff);
        given(doseLogRepository.findByScheduleIdAndStatusFrom(
                SCHEDULE_ID, DoseStatus.PENDING, afterCutoff))
                .willReturn(List.of(pendingLog));

        sut.skipPendingAfter(SCHEDULE_ID, cutoff);

        assertThat(pendingLog.getStatus()).isEqualTo(DoseStatus.SKIPPED);
        assertThat(pendingLog.getSkipReason()).isEqualTo("기간 변경");
        verify(doseLogRepository).save(pendingLog);
    }

    @Test
    @DisplayName("skipPendingAfter — 해당 기간에 로그 없으면 아무것도 안 함")
    void skipPendingAfter_noLogs_doesNothing() {
        LocalDate cutoff = LocalDate.of(2026, 6, 30);
        Instant afterCutoff = policy.startOfNextDay(cutoff);
        given(doseLogRepository.findByScheduleIdAndStatusFrom(
                SCHEDULE_ID, DoseStatus.PENDING, afterCutoff))
                .willReturn(List.of());

        sut.skipPendingAfter(SCHEDULE_ID, cutoff);

        verify(doseLogRepository, never()).save(any());
    }
}
