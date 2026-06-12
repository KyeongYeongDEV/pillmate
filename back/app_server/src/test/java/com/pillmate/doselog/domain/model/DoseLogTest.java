package com.pillmate.doselog.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DoseLog 도메인 — 복용 체크 멱등 + delayed 판정")
class DoseLogTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-05-25T09:00:00Z");
    private static final Clock FIXED = Clock.fixed(FIXED_NOW, ZoneId.of("UTC"));

    @Test
    @DisplayName("PENDING 에서 take() 호출 시 TAKEN 으로 전이")
    void check_whenPending_becomesTaken() {
        DoseLog log = DoseLog.of(1L, 2L, FIXED_NOW);

        log.take(99L, FIXED);

        assertThat(log.getStatus()).isEqualTo(DoseStatus.TAKEN);
        assertThat(log.getCheckedBy()).isEqualTo(99L);
        assertThat(log.getCheckedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    @DisplayName("이미 TAKEN 상태에서 take() 다시 호출해도 멱등 (예외 없음, checkedBy 유지)")
    void check_whenAlreadyTaken_isIdempotent() {
        DoseLog log = DoseLog.of(1L, 2L, FIXED_NOW);
        log.take(99L, FIXED);

        log.take(77L, FIXED);

        assertThat(log.getStatus()).isEqualTo(DoseStatus.TAKEN);
        assertThat(log.getCheckedBy()).isEqualTo(99L);
    }

    @Test
    @DisplayName("skip() 호출 시 reason 보존")
    void skip_savesReason() {
        DoseLog log = DoseLog.of(1L, 2L, FIXED_NOW);

        log.skip(99L, "외출 중", FIXED);

        assertThat(log.getStatus()).isEqualTo(DoseStatus.SKIPPED);
        assertThat(log.getSkipReason()).isEqualTo("외출 중");
    }

    @Test
    @DisplayName("예정 시각보다 30분 이상 지난 PENDING 은 delayed 로 판정")
    void delayedDetection_basedOnScheduledAt() {
        Instant scheduled = FIXED_NOW.minus(Duration.ofMinutes(45));
        DoseLog log = DoseLog.of(1L, 2L, scheduled);

        assertThat(log.isDelayed(FIXED)).isTrue();
    }

    @Test
    @DisplayName("예정 시각으로부터 30분 이내면 delayed 아님")
    void notDelayed_withinThreshold() {
        Instant scheduled = FIXED_NOW.minus(Duration.ofMinutes(10));
        DoseLog log = DoseLog.of(1L, 2L, scheduled);

        assertThat(log.isDelayed(FIXED)).isFalse();
    }

    @Test
    @DisplayName("TAKEN 에서 cancel() — PENDING 복귀 + checkedBy/checkedAt/skipReason 초기화")
    void cancel_whenTaken_revertsToPendingAndClearsCheckFields() {
        DoseLog log = DoseLog.of(1L, 2L, FIXED_NOW);
        log.take(99L, FIXED);

        log.cancel();

        assertThat(log.getStatus()).isEqualTo(DoseStatus.PENDING);
        assertThat(log.getCheckedBy()).isNull();
        assertThat(log.getCheckedAt()).isNull();
        assertThat(log.getSkipReason()).isNull();
    }

    @Test
    @DisplayName("cancel() 은 groupNotifiedAt 도 초기화 — 재복용 시 그룹 알림 다시 발송 가능")
    void cancel_whenTaken_clearsGroupNotifiedAt() {
        DoseLog log = DoseLog.of(1L, 2L, FIXED_NOW);
        log.take(99L, FIXED);
        log.markGroupNotified(FIXED_NOW);

        log.cancel();

        assertThat(log.isGroupNotified()).isFalse();
        assertThat(log.getGroupNotifiedAt()).isNull();
    }

    @Test
    @DisplayName("PENDING 에서 cancel() — 멱등 no-op (도메인 판단: FE 중복 요청 안전)")
    void cancel_whenPending_isNoOp() {
        DoseLog log = DoseLog.of(1L, 2L, FIXED_NOW);

        log.cancel();

        assertThat(log.getStatus()).isEqualTo(DoseStatus.PENDING);
    }

    @Test
    @DisplayName("SKIPPED 에서 cancel() — no-op (CANCEL 은 TAKEN 정정 전용, SKIP 의미 유지)")
    void cancel_whenSkipped_isNoOp() {
        DoseLog log = DoseLog.of(1L, 2L, FIXED_NOW);
        log.skip(99L, "외출 중", FIXED);

        log.cancel();

        assertThat(log.getStatus()).isEqualTo(DoseStatus.SKIPPED);
        assertThat(log.getSkipReason()).isEqualTo("외출 중");
    }

    @Test
    @DisplayName("markGroupNotified() — 발송 시각 기록 + isGroupNotified true")
    void markGroupNotified_recordsTimestamp() {
        DoseLog log = DoseLog.of(1L, 2L, FIXED_NOW);
        log.take(99L, FIXED);

        log.markGroupNotified(FIXED_NOW);

        assertThat(log.isGroupNotified()).isTrue();
        assertThat(log.getGroupNotifiedAt()).isEqualTo(FIXED_NOW);
    }
}
