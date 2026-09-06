package com.pillmate.caregroup.domain;

import com.pillmate.caregroup.domain.model.MedicationShareGrant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MedicationShareGrant 도메인 — 알약 정보(L2) 공유 관계 생성")
class MedicationShareGrantTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-08-20T09:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("of() 생성 시 careGroupId/ownerUserId/viewerUserId/grantedAt 세팅")
    void of_setsAllFields() {
        MedicationShareGrant grant = MedicationShareGrant.of(1L, 10L, 20L, FIXED_CLOCK);

        assertThat(grant.getCareGroupId()).isEqualTo(1L);
        assertThat(grant.getOwnerUserId()).isEqualTo(10L);
        assertThat(grant.getViewerUserId()).isEqualTo(20L);
        assertThat(grant.getGrantedAt()).isEqualTo(Instant.parse("2026-08-20T09:00:00Z"));
    }
}
