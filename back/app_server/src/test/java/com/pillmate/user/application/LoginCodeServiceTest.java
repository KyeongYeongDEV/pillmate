package com.pillmate.user.application;

import com.pillmate.common.exception.PillmateException;
import com.pillmate.common.exception.ErrorCode;
import com.pillmate.user.application.dto.AuthResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("LoginCodeService — 카카오 콜백 1회용 코드 교환")
class LoginCodeServiceTest {

    private final LoginCodeService sut = new LoginCodeService();

    private final AuthResult sampleResult = new AuthResult(
            "jwt.token", 1L, false, new AuthResult.ProfileInfo("홍길동", "test@test.com", null));

    @Test
    @DisplayName("generate → exchange: 정상 교환 시 AuthResult 반환")
    void generate_thenExchange_returnsResult() {
        String code = sut.generate(sampleResult);

        AuthResult result = sut.exchange(code);

        assertThat(result.token()).isEqualTo("jwt.token");
        assertThat(result.userId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("코드는 1회만 사용 가능 — 두 번째 exchange는 NOT_FOUND 예외")
    void exchange_twice_throwsOnSecondUse() {
        String code = sut.generate(sampleResult);
        sut.exchange(code);

        assertThatThrownBy(() -> sut.exchange(code))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LOGIN_CODE_NOT_FOUND);
    }

    @Test
    @DisplayName("존재하지 않는 코드 → LOGIN_CODE_NOT_FOUND")
    void exchange_unknownCode_throws() {
        assertThatThrownBy(() -> sut.exchange("invalid-code"))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LOGIN_CODE_NOT_FOUND);
    }
}
