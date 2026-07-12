package com.pillmate.common.security;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("production")
@Component
public class ProductionSecurityValidator {

    static final String DEV_SECRET_PREFIX = "dev-only-insecure";
    static final String PLACEHOLDER_MARKER = "change_me";
    static final int MIN_SECRET_LENGTH = 32;

    @Value("${pillmate.auth.jwt.secret:}")
    private String jwtSecret;

    @Value("${pillmate.auth.dev-fallback-enabled:false}")
    private boolean devFallbackEnabled;

    @PostConstruct
    void validate() {
        requireStrongJwtSecret();
        requireDevFallbackDisabled();
    }

    private void requireStrongJwtSecret() {
        if (isWeakSecret(jwtSecret)) {
            throw new IllegalStateException(
                    "PILLMATE_JWT_SECRET must be set to a secure value (>= " + MIN_SECRET_LENGTH +
                    " chars, non-placeholder) in production. Set the PILLMATE_JWT_SECRET environment variable.");
        }
    }

    private boolean isWeakSecret(String secret) {
        return secret == null
                || secret.isBlank()
                || secret.startsWith(DEV_SECRET_PREFIX)
                || secret.contains(PLACEHOLDER_MARKER)
                || secret.length() < MIN_SECRET_LENGTH;
    }

    private void requireDevFallbackDisabled() {
        if (devFallbackEnabled) {
            throw new IllegalStateException(
                    "PILLMATE_DEV_FALLBACK must be disabled in production (dev-fallback lets X-User-Id headers " +
                    "forge identities). Set pillmate.auth.dev-fallback-enabled=false.");
        }
    }
}
