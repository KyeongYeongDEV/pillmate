package com.pillmate.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ProductionSecurityValidator — JWT 시크릿 기동 시 검증")
class ProductionSecurityValidatorTest {

    private ProductionSecurityValidator newValidator(String secret) {
        return newValidator(secret, false);
    }

    private ProductionSecurityValidator newValidator(String secret, boolean devFallbackEnabled) {
        ProductionSecurityValidator v = new ProductionSecurityValidator();
        ReflectionTestUtils.setField(v, "jwtSecret", secret);
        ReflectionTestUtils.setField(v, "devFallbackEnabled", devFallbackEnabled);
        return v;
    }

    @Test
    @DisplayName("dev 기본 시크릿 접두사 → 기동 실패")
    void validate_devSecretPrefix_throwsIllegalState() {
        ProductionSecurityValidator v = newValidator("dev-only-insecure-secret-do-not-use");

        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PILLMATE_JWT_SECRET");
    }

    @Test
    @DisplayName("빈 시크릿 → 기동 실패")
    void validate_blankSecret_throwsIllegalState() {
        ProductionSecurityValidator v = newValidator("   ");

        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("placeholder(change_me) 시크릿 → 기동 실패")
    void validate_placeholderSecret_throwsIllegalState() {
        ProductionSecurityValidator v = newValidator("change_me_please_this_is_not_secure_at_all");

        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PILLMATE_JWT_SECRET");
    }

    @Test
    @DisplayName("32자 미만 시크릿 → 기동 실패")
    void validate_tooShortSecret_throwsIllegalState() {
        ProductionSecurityValidator v = newValidator("short-secret-31-chars-exactly!!");

        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PILLMATE_JWT_SECRET");
    }

    @Test
    @DisplayName("강한 시크릿이라도 dev-fallback=true → 기동 실패")
    void validate_devFallbackEnabled_throwsIllegalState() {
        ProductionSecurityValidator v = newValidator(
                "super-secure-random-secret-value-at-least-32-chars", true);

        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PILLMATE_DEV_FALLBACK");
    }

    @Test
    @DisplayName("강한 시크릿 + dev-fallback=false → 정상 기동")
    void validate_secureSecret_passes() {
        ProductionSecurityValidator v = newValidator("super-secure-random-secret-value-at-least-32-chars");

        assertThatCode(v::validate).doesNotThrowAnyException();
    }
}
