package com.pillmate.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SentryDsnValidator — io.sentry.Dsn 파서를 통한 DSN 형식 판정")
class SentryDsnValidatorTest {

    @Test
    @DisplayName("공개키(public key)가 없는 DSN은 무효 판정하고 원인 예외명을 반환한다")
    void invalidReason_whenPublicKeyMissing_returnsReason() {
        // given
        String dsnWithoutPublicKey = "https://o451162.ingest.sentry.io/4507000000000000";

        // when
        var result = SentryDsnValidator.invalidReason(dsnWithoutPublicKey);

        // then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo("IllegalArgumentException");
    }

    @Test
    @DisplayName("정상 형식 DSN은 유효 판정한다")
    void invalidReason_whenDsnValid_returnsEmpty() {
        // given
        String validDsn = "https://abc123publickey@o451162.ingest.sentry.io/4507000000000000";

        // when
        var result = SentryDsnValidator.invalidReason(validDsn);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("스킴이 아예 없는 문자열은 무효 판정한다")
    void invalidReason_whenNotAUri_returnsReason() {
        // given
        String garbage = "not-a-dsn";

        // when
        var result = SentryDsnValidator.invalidReason(garbage);

        // then
        assertThat(result).isPresent();
    }
}
