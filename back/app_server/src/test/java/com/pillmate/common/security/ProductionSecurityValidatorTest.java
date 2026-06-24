package com.pillmate.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ProductionSecurityValidator — JWT 시크릿 기동 시 검증")
class ProductionSecurityValidatorTest {

    private ProductionSecurityValidator newValidator(String secret) {
        ProductionSecurityValidator v = new ProductionSecurityValidator();
        ReflectionTestUtils.setField(v, "jwtSecret", secret);
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
    @DisplayName("강한 시크릿 → 정상 기동")
    void validate_secureSecret_passes() {
        ProductionSecurityValidator v = newValidator("super-secure-random-secret-value-at-least-32-chars");

        assertThatCode(v::validate).doesNotThrowAnyException();
    }
}
