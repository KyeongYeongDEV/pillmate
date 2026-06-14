package com.pillmate.caregroup.domain;

import com.pillmate.caregroup.domain.model.MemberRole;
import com.pillmate.caregroup.domain.model.Membership;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Membership 도메인 — 역할 식별")
class MembershipTest {

    @Test
    @DisplayName("ADMIN 역할이면 isAdmin() true")
    void isAdmin_whenRoleAdmin_true() {
        Membership m = Membership.of(1L, 1L, MemberRole.ADMIN, null);

        assertThat(m.isAdmin()).isTrue();
    }

    @Test
    @DisplayName("PATIENT 역할이면 isAdmin() false")
    void isAdmin_whenRolePatient_false() {
        Membership m = Membership.of(1L, 2L, MemberRole.PATIENT, 1L);

        assertThat(m.isAdmin()).isFalse();
    }

    @Test
    @DisplayName("기본 pinned=false")
    void create_defaultNotPinned() {
        Membership m = Membership.of(1L, 1L, MemberRole.ADMIN, null);

        assertThat(m.isPinned()).isFalse();
    }

    @Test
    @DisplayName("pin() 호출 시 isPinned=true")
    void pin_marksPinnedTrue() {
        Membership m = Membership.of(1L, 1L, MemberRole.ADMIN, null);

        m.pin();

        assertThat(m.isPinned()).isTrue();
    }

    @Test
    @DisplayName("unpin() 호출 시 isPinned=false")
    void unpin_marksPinnedFalse() {
        Membership m = Membership.of(1L, 1L, MemberRole.ADMIN, null);
        m.pin();

        m.unpin();

        assertThat(m.isPinned()).isFalse();
    }

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-14T09:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("생성 직후 status=ACTIVE, leftAt=null")
    void create_defaultActive() {
        Membership m = Membership.of(1L, 1L, MemberRole.ADMIN, null);

        assertThat(m.isActive()).isTrue();
        assertThat(m.hasLeft()).isFalse();
        assertThat(m.getLeftAt()).isNull();
    }

    @Test
    @DisplayName("leave() 호출 시 ACTIVE→LEFT 전이 + leftAt 기록")
    void leave_marksLeftAndRecordsLeftAt() {
        Membership m = Membership.of(10L, 2L, MemberRole.PATIENT, 1L);

        m.leave(FIXED_CLOCK);

        assertThat(m.hasLeft()).isTrue();
        assertThat(m.isActive()).isFalse();
        assertThat(m.getLeftAt()).isEqualTo(Instant.parse("2026-06-14T09:00:00Z"));
    }

    @Test
    @DisplayName("leave() 호출 시 핀 자동 해제")
    void leave_unpinsPinnedGroup() {
        Membership m = Membership.of(10L, 2L, MemberRole.PATIENT, 1L);
        m.pin();

        m.leave(FIXED_CLOCK);

        assertThat(m.isPinned()).isFalse();
    }

    @Test
    @DisplayName("이미 LEFT 상태에서 leave() 재호출 멱등 — leftAt 변경 없음")
    void leave_whenAlreadyLeft_isIdempotent() {
        Membership m = Membership.of(10L, 2L, MemberRole.PATIENT, 1L);
        m.leave(FIXED_CLOCK);
        Instant firstLeftAt = m.getLeftAt();

        Clock laterClock = Clock.fixed(Instant.parse("2026-06-15T12:00:00Z"), ZoneOffset.UTC);
        m.leave(laterClock);

        assertThat(m.hasLeft()).isTrue();
        assertThat(m.getLeftAt()).isEqualTo(firstLeftAt);
    }
}
