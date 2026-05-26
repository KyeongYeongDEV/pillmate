package com.pillmate.caregroup.domain;

import com.pillmate.caregroup.domain.model.CareGroup;
import com.pillmate.caregroup.domain.model.InviteCode;
import com.pillmate.caregroup.domain.model.Membership;
import com.pillmate.caregroup.domain.model.MemberRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CareGroup 도메인")
class CareGroupTest {

    @Test
    @DisplayName("그룹 생성 시 생성자는 ADMIN으로 등록된다")
    void create_creatorBecomesAdmin() {
        CareGroup group = CareGroup.create("우리 가족", 1L);

        assertThat(group.getName()).isEqualTo("우리 가족");
        assertThat(group.getCreatedBy()).isEqualTo(1L);
    }

    @Test
    @DisplayName("초대 코드는 6자리 영숫자")
    void inviteCode_is6Chars() {
        InviteCode code = InviteCode.generate(1L, 1L);

        assertThat(code.getCode()).hasSize(6);
        assertThat(code.getCode()).matches("[A-Z0-9]{6}");
        assertThat(code.isExpired()).isFalse();
    }

    @Test
    @DisplayName("만료된 초대 코드는 isExpired()가 true")
    void expiredCode_isExpired() {
        InviteCode code = InviteCode.ofExpired("ABC123", 1L, 1L);

        assertThat(code.isExpired()).isTrue();
    }

    @Test
    @DisplayName("이미 사용된 초대 코드는 isUsable()이 false")
    void usedCode_isNotUsable() {
        InviteCode code = InviteCode.generate(1L, 1L);
        code.markUsed();

        assertThat(code.isUsable()).isFalse();
    }

    @Test
    @DisplayName("Membership에 PATIENT 역할 부여")
    void membership_patientRole() {
        Membership m = Membership.of(1L, 2L, MemberRole.PATIENT, null);

        assertThat(m.getRole()).isEqualTo(MemberRole.PATIENT);
        assertThat(m.isAdmin()).isFalse();
    }

    @Test
    @DisplayName("Membership에 ADMIN 역할이면 isAdmin() true")
    void membership_adminRole() {
        Membership m = Membership.of(1L, 1L, MemberRole.ADMIN, null);

        assertThat(m.isAdmin()).isTrue();
    }
}
