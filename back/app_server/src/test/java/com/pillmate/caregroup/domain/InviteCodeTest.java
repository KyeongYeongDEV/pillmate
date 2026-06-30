package com.pillmate.caregroup.domain;

import com.pillmate.caregroup.domain.model.InviteCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("InviteCode 도메인 — 생성/만료/사용")
class InviteCodeTest {

    @Test
    @DisplayName("generate() 는 6자리 A-Z/0-9 코드 발급")
    void generate_returnsRandom6CharCode() {
        InviteCode code = InviteCode.generate(1L, 1L);

        assertThat(code.getCode()).hasSize(6);
        assertThat(code.getCode()).matches("[A-Z0-9]{6}");
        assertThat(code.getExpiresAt()).isNotNull();
        assertThat(code.isExpired()).isFalse();
    }

    @Test
    @DisplayName("generate() 발급 코드의 expiresAt 은 발급 시각 + 60초")
    void generate_expiresAt_isCreatedAtPlus60Seconds() {
        Instant before = Instant.now();
        InviteCode code = InviteCode.generate(1L, 1L);
        Instant after = Instant.now();

        assertThat(InviteCode.INVITE_CODE_TTL_SECONDS).isEqualTo(60);
        assertThat(code.getExpiresAt())
                .isAfterOrEqualTo(before.plus(60, ChronoUnit.SECONDS))
                .isBeforeOrEqualTo(after.plus(60, ChronoUnit.SECONDS).plusSeconds(1));
    }

    @Test
    @DisplayName("consume() 호출 시 만료된 코드는 IllegalStateException")
    void consume_whenExpired_throws() {
        InviteCode expired = InviteCode.ofExpired("ABC123", 1L, 1L);

        assertThatThrownBy(expired::consume)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expired");
    }

    @Test
    @DisplayName("consume() 호출 시 이미 사용된 코드는 IllegalStateException")
    void consume_whenAlreadyUsed_throws() {
        InviteCode code = InviteCode.generate(1L, 1L);
        code.consume();

        assertThatThrownBy(code::consume)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("used");
    }

    @Test
    @DisplayName("consume() 정상 호출 시 usedAt 이 기록되고 isUsable false")
    void consume_marksUsedAt() {
        InviteCode code = InviteCode.generate(1L, 1L);

        code.consume();

        assertThat(code.getUsedAt()).isNotNull();
        assertThat(code.isUsable()).isFalse();
    }
}
