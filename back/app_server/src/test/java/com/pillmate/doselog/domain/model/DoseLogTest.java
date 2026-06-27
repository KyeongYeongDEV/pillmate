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

    @Test
    @DisplayName("reschedule — PENDING 은 scheduledAt 갱신 (시각 변경 반영)")
    void reschedule_whenPending_updatesScheduledAt() {
        DoseLog log = DoseLog.of(1L, 2L, FIXED_NOW);
        Instant newAt = FIXED_NOW.plus(Duration.ofHours(1));

        log.reschedule(newAt);

        assertThat(log.getScheduledAt()).isEqualTo(newAt);
    }

    @Test
    @DisplayName("reschedule — TAKEN 은 no-op (완료 기록 보존, 재복용 방지)")
    void reschedule_whenTaken_isNoOp() {
        DoseLog log = DoseLog.of(1L, 2L, FIXED_NOW);
        log.take(99L, FIXED);
        Instant newAt = FIXED_NOW.plus(Duration.ofHours(1));

        log.reschedule(newAt);

        assertThat(log.getScheduledAt()).isEqualTo(FIXED_NOW);
        assertThat(log.getStatus()).isEqualTo(DoseStatus.TAKEN);
    }

    @Test
    @DisplayName("isEditableOn — scheduledAt KST 날짜 == 오늘 KST 날짜면 true")
    void isEditableOn_whenScheduledToday_returnsTrue() {
        // given — now: KST 2026-06-12 19:00, scheduled: KST 2026-06-12 08:00
        Clock now = kstClock("2026-06-12T10:00:00Z");
        DoseLog log = DoseLog.of(1L, 2L, Instant.parse("2026-06-11T23:00:00Z"));

        // when / then
        assertThat(log.isEditableOn(now)).isTrue();
    }

    @Test
    @DisplayName("isEditableOn — 어제(KST) 예정분은 false")
    void isEditableOn_whenScheduledYesterday_returnsFalse() {
        // given — now: KST 2026-06-12 10:00, scheduled: KST 2026-06-11 08:00
        Clock now = kstClock("2026-06-12T01:00:00Z");
        DoseLog log = DoseLog.of(1L, 2L, Instant.parse("2026-06-10T23:00:00Z"));

        // when / then
        assertThat(log.isEditableOn(now)).isFalse();
    }

    @Test
    @DisplayName("isEditableOn — 내일(KST) 예정분은 false")
    void isEditableOn_whenScheduledTomorrow_returnsFalse() {
        // given — now: KST 2026-06-12 10:00, scheduled: KST 2026-06-13 08:00
        Clock now = kstClock("2026-06-12T01:00:00Z");
        DoseLog log = DoseLog.of(1L, 2L, Instant.parse("2026-06-12T23:00:00Z"));

        // when / then
        assertThat(log.isEditableOn(now)).isFalse();
    }

    @Test
    @DisplayName("isEditableOn — 오늘 KST 23:59 에도 당일분은 true")
    void isEditableOn_atEndOfKstDay_returnsTrue() {
        // given — now: KST 2026-06-12 23:59, scheduled: KST 2026-06-12 08:00
        Clock now = kstClock("2026-06-12T14:59:00Z");
        DoseLog log = DoseLog.of(1L, 2L, Instant.parse("2026-06-11T23:00:00Z"));

        // when / then
        assertThat(log.isEditableOn(now)).isTrue();
    }

    @Test
    @DisplayName("isEditableOn — KST 자정 경계(UTC 15:00): 자정 직후엔 전날분 false, 당일분 true")
    void isEditableOn_atKstMidnightBoundary() {
        // given — now: UTC 2026-06-11 15:00 == KST 2026-06-12 00:00
        Clock now = kstClock("2026-06-11T15:00:00Z");
        DoseLog yesterdayLog = DoseLog.of(1L, 2L, Instant.parse("2026-06-11T14:59:59Z"));
        DoseLog todayLog = DoseLog.of(1L, 2L, Instant.parse("2026-06-11T23:00:00Z"));

        // when / then — KST 2026-06-11 23:59:59 분은 잠김, KST 2026-06-12 08:00 분은 허용
        assertThat(yesterdayLog.isEditableOn(now)).isFalse();
        assertThat(todayLog.isEditableOn(now)).isTrue();
    }

    @Test
    @DisplayName("cancelForPeriodChange() — PENDING → SKIPPED(기간 변경), skipReason 설정")
    void cancelForPeriodChange_pending_becomesSkipped() {
        DoseLog log = DoseLog.of(1L, 2L, FIXED_NOW);

        log.cancelForPeriodChange();

        assertThat(log.getStatus()).isEqualTo(DoseStatus.SKIPPED);
        assertThat(log.getSkipReason()).isEqualTo("기간 변경");
    }

    @Test
    @DisplayName("cancelForPeriodChange() — TAKEN 은 no-op (복용 완료 기록 보존)")
    void cancelForPeriodChange_taken_isNoOp() {
        DoseLog log = DoseLog.of(1L, 2L, FIXED_NOW);
        log.take(99L, FIXED);

        log.cancelForPeriodChange();

        assertThat(log.getStatus()).isEqualTo(DoseStatus.TAKEN);
    }

    private Clock kstClock(String utcInstant) {
        return Clock.fixed(Instant.parse(utcInstant), ZoneId.of("Asia/Seoul"));
    }
}
