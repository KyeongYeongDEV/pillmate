package com.pillmate.doselog.domain;

import com.pillmate.common.exception.PillmateException;
import com.pillmate.doselog.domain.model.DoseLog;
import com.pillmate.doselog.domain.model.DoseStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DoseLog 도메인 — 복용 상태 머신")
class DoseLogTest {

    @Test
    @DisplayName("생성 시 PENDING 상태")
    void create_isPending() {
        DoseLog log = DoseLog.of(1L, 1L, Instant.now());

        assertThat(log.getStatus()).isEqualTo(DoseStatus.PENDING);
    }

    @Test
    @DisplayName("PENDING → TAKEN 전이 성공")
    void take_fromPending() {
        DoseLog log = DoseLog.of(1L, 1L, Instant.now());
        log.take(1L);

        assertThat(log.getStatus()).isEqualTo(DoseStatus.TAKEN);
        assertThat(log.getCheckedBy()).isEqualTo(1L);
        assertThat(log.getCheckedAt()).isNotNull();
    }

    @Test
    @DisplayName("PENDING → SKIPPED 전이 성공")
    void skip_fromPending() {
        DoseLog log = DoseLog.of(1L, 1L, Instant.now());
        log.skip(1L, "외출 중");

        assertThat(log.getStatus()).isEqualTo(DoseStatus.SKIPPED);
        assertThat(log.getSkipReason()).isEqualTo("외출 중");
    }

    @Test
    @DisplayName("이미 TAKEN이면 다시 take() 불가 — 예외 발생")
    void take_alreadyTaken_throws() {
        DoseLog log = DoseLog.of(1L, 1L, Instant.now());
        log.take(1L);

        assertThatThrownBy(() -> log.take(1L))
                .isInstanceOf(PillmateException.class);
    }

    @Test
    @DisplayName("이미 SKIPPED이면 take() 불가")
    void take_alreadySkipped_throws() {
        DoseLog log = DoseLog.of(1L, 1L, Instant.now());
        log.skip(1L, "이유");

        assertThatThrownBy(() -> log.take(1L))
                .isInstanceOf(PillmateException.class);
    }
}
