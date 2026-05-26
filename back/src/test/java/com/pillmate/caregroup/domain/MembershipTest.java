package com.pillmate.caregroup.domain;

import com.pillmate.caregroup.domain.model.MemberRole;
import com.pillmate.caregroup.domain.model.Membership;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}
