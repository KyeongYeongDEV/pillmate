package com.pillmate.common.security;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("production")
@Component
public class ProductionSecurityValidator {

    static final String DEV_SECRET_PREFIX = "dev-only-insecure";

    @Value("${pillmate.auth.jwt.secret:}")
    private String jwtSecret;

    @PostConstruct
    void validate() {
        if (jwtSecret == null || jwtSecret.isBlank() || jwtSecret.startsWith(DEV_SECRET_PREFIX)) {
            throw new IllegalStateException(
                    "PILLMATE_JWT_SECRET must be set to a secure value in production. " +
                    "Set the PILLMATE_JWT_SECRET environment variable.");
        }
    }
}
