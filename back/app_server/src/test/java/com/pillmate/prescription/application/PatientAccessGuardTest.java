package com.pillmate.prescription.application;

import com.pillmate.common.exception.PillmateException;
import com.pillmate.common.security.PatientAccessGuard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PatientAccessGuard — 본인 데이터만 접근 허용")
class PatientAccessGuardTest {

    private final PatientAccessGuard sut = new PatientAccessGuard();

    @Test
    @DisplayName("본인이면 접근 허용")
    void canAccess_self_returnsTrue() {
        assertThat(sut.canAccess(1L, 1L)).isTrue();
    }

    @Test
    @DisplayName("다른 user는 접근 거부")
    void canAccess_otherUser_returnsFalse() {
        assertThat(sut.canAccess(1L, 2L)).isFalse();
    }

    @Test
    @DisplayName("viewerId null이면 접근 거부")
    void canAccess_nullViewer_returnsFalse() {
        assertThat(sut.canAccess(null, 1L)).isFalse();
    }

    @Test
    @DisplayName("requireAccess 실패 시 PillmateException 발생")
    void requireAccess_notAllowed_throws() {
        assertThatThrownBy(() -> sut.requireAccess(5L, 3L))
                .isInstanceOf(PillmateException.class);
    }
}
