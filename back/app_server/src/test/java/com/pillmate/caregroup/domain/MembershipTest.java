package com.pillmate.caregroup.domain;

import com.pillmate.caregroup.domain.model.MemberRole;
import com.pillmate.caregroup.domain.model.Membership;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    // T-GROUP-NICKNAME: 그룹별 별명 — 기본값은 null(=표시할 때 실제 이름으로 폴백)
    @Test
    @DisplayName("생성 직후 nickname=null (기본값은 본인 원래 이름으로 폴백)")
    void create_defaultNicknameNull() {
        Membership m = Membership.of(1L, 1L, MemberRole.ADMIN, null);

        assertThat(m.getNickname()).isNull();
    }

    @Test
    @DisplayName("updateNickname — 정상 값이면 trim 후 저장")
    void updateNickname_trimsAndSets() {
        Membership m = Membership.of(1L, 1L, MemberRole.ADMIN, null);

        m.updateNickname("  삼촌  ");

        assertThat(m.getNickname()).isEqualTo("삼촌");
    }

    @Test
    @DisplayName("updateNickname — null/공백이면 nickname null 로 리셋(기본값=원래 이름 복귀)")
    void updateNickname_blankResetsToNull() {
        Membership m = Membership.of(1L, 1L, MemberRole.ADMIN, null);
        m.updateNickname("삼촌");

        m.updateNickname("   ");

        assertThat(m.getNickname()).isNull();
    }

    @Test
    @DisplayName("updateNickname — 20자 초과면 IllegalArgumentException")
    void updateNickname_tooLong_throws() {
        Membership m = Membership.of(1L, 1L, MemberRole.ADMIN, null);

        assertThatThrownBy(() -> m.updateNickname("가".repeat(21)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
