package com.pillmate.user.application;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.user.application.dto.AuthResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class LoginCodeService {

    private static final Duration CODE_TTL = Duration.ofSeconds(60);
    private final ConcurrentHashMap<String, PendingLogin> pendingLogins = new ConcurrentHashMap<>();

    public String generate(AuthResult result) {
        evictExpired();
        String code = UUID.randomUUID().toString();
        pendingLogins.put(code, new PendingLogin(result, Instant.now().plus(CODE_TTL)));
        return code;
    }

    public AuthResult exchange(String code) {
        PendingLogin pending = pendingLogins.remove(code);
        if (pending == null) {
            throw new PillmateException(ErrorCode.LOGIN_CODE_NOT_FOUND);
        }
        if (Instant.now().isAfter(pending.expiresAt())) {
            throw new PillmateException(ErrorCode.LOGIN_CODE_EXPIRED);
        }
        return pending.result();
    }

    private void evictExpired() {
        Instant now = Instant.now();
        pendingLogins.entrySet().removeIf(e -> now.isAfter(e.getValue().expiresAt()));
    }

    private record PendingLogin(AuthResult result, Instant expiresAt) {}
}
