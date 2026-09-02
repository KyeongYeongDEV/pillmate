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
    @DisplayName("cancel() 이 remindedAt 은 유지 — 체크 취소해도 같은 시각 리마인더 재발송 금지")
    void cancel_keepsRemindedAt() {
        DoseLog log = DoseLog.of(1L, 2L, FIXED_NOW);
        org.springframework.test.util.ReflectionTestUtils.setField(log, "remindedAt", FIXED_NOW);
        log.take(2L, FIXED);

        log.cancel();

        assertThat(log.getStatus()).isEqualTo(DoseStatus.PENDING);
        assertThat(log.getRemindedAt()).isEqualTo(FIXED_NOW);
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

    // T-CANCEL-IDEMPOTENT-GUARD: cancel() boolean 반환 — 실제 전이 시만 true (publish/append 중복 적재 방지)
    @Test
    @DisplayName("TAKEN 에서 cancel() — 실제 전이 → true, PENDING + checkedAt null")
    void cancel_whenTaken_returnsTrue() {
        DoseLog log = DoseLog.of(1L, 2L, FIXED_NOW);
        log.take(99L, FIXED);

        boolean transitioned = log.cancel();

        assertThat(transitioned).isTrue();
        assertThat(log.getStatus()).isEqualTo(DoseStatus.PENDING);
        assertThat(log.getCheckedAt()).isNull();
    }

    @Test
    @DisplayName("이미 PENDING 에서 cancel() — 전이 없음 → false, status 불변")
    void cancel_whenAlreadyPending_returnsFalse() {
        DoseLog log = DoseLog.of(1L, 2L, FIXED_NOW);

        boolean transitioned = log.cancel();

        assertThat(transitioned).isFalse();
        assertThat(log.getStatus()).isEqualTo(DoseStatus.PENDING);
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
    @DisplayName("reschedule — 이미 리마인드된 PENDING 도 remindedAt 리셋 (변경된 새 시각에 알림 재발송 가능)")
    void reschedule_whenAlreadyReminded_resetsRemindedAt() {
        DoseLog log = DoseLog.of(1L, 2L, FIXED_NOW);
        org.springframework.test.util.ReflectionTestUtils.setField(log, "remindedAt", FIXED_NOW);
        Instant newAt = FIXED_NOW.plus(Duration.ofHours(1));

        log.reschedule(newAt);

        assertThat(log.getScheduledAt()).isEqualTo(newAt);
        assertThat(log.getRemindedAt()).isNull();
    }

    @Test
    @DisplayName("reschedule — 동일 시각으로 재호출해도 remindedAt 유지 (폼 재제출 시 중복 알림 방지)")
    void reschedule_withSameScheduledAt_keepsRemindedAt() {
        DoseLog log = DoseLog.of(1L, 2L, FIXED_NOW);
        org.springframework.test.util.ReflectionTestUtils.setField(log, "remindedAt", FIXED_NOW);

        log.reschedule(FIXED_NOW);

        assertThat(log.getScheduledAt()).isEqualTo(FIXED_NOW);
        assertThat(log.getRemindedAt()).isEqualTo(FIXED_NOW);
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

    // T-SESSION-2026-06-29-RETRO Group C: isEditableOn → 항상 true (사용자 명시 동의 — 까먹은 약 체크 허용).
    // 과거 시각 경계(어제/내일/자정) 잠금 테스트는 제거된 동작이라 폐기, 아래 always-true 계약으로 대체.
    @Test
    @DisplayName("isEditableOn — 어제/오늘/내일/먼 과거 어떤 날짜든 항상 true (시간 경계 완화)")
    void isEditableOn_anyDate_returnsTrue() {
        // given — now: KST 2026-06-12 10:00
        Clock now = kstClock("2026-06-12T01:00:00Z");
        DoseLog today = DoseLog.of(1L, 2L, Instant.parse("2026-06-11T23:00:00Z"));      // KST 2026-06-12
        DoseLog yesterday = DoseLog.of(1L, 2L, Instant.parse("2026-06-10T23:00:00Z"));  // KST 2026-06-11
        DoseLog tomorrow = DoseLog.of(1L, 2L, Instant.parse("2026-06-12T23:00:00Z"));   // KST 2026-06-13
        DoseLog farPast = DoseLog.of(1L, 2L, Instant.parse("2026-01-01T00:00:00Z"));

        // when / then — 모두 편집 가능
        assertThat(today.isEditableOn(now)).isTrue();
        assertThat(yesterday.isEditableOn(now)).isTrue();
        assertThat(tomorrow.isEditableOn(now)).isTrue();
        assertThat(farPast.isEditableOn(now)).isTrue();
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
    @DisplayName("새로 생성된 DoseLog — overdueNotifiedAt 초기값 null (지연 알림 폴러 원자 클레임 대상)")
    void of_initial_overdueNotifiedAtIsNull() {
        DoseLog log = DoseLog.of(1L, 2L, FIXED_NOW);

        assertThat(log.getOverdueNotifiedAt()).isNull();
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
