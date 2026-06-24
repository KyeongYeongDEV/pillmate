package com.pillmate.common.security;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final Duration accessTtl;

    public JwtTokenProvider(
            @Value("${pillmate.auth.jwt.secret}") String secret,
            @Value("${pillmate.auth.jwt.access-ttl-days:14}") long accessTtlDays) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "PILLMATE_JWT_SECRET is not set. Set the PILLMATE_JWT_SECRET environment variable.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtl = Duration.ofDays(accessTtlDays);
    }

    public String issue(Long userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .signWith(key)
                .compact();
    }

    public Long parseUserId(String jwt) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(jwt)
                    .getPayload();
            return Long.parseLong(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            throw new PillmateException(ErrorCode.INVALID_AUTH_TOKEN);
        }
    }
}
