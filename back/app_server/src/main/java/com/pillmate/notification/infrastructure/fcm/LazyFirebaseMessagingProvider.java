package com.pillmate.notification.infrastructure.fcm;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;

/**
 * FirebaseApp 을 lazy + 1회 초기화. 자격증명이 없거나 잘못되어도 startup crash 없이 빈 Optional 반환.
 * (운영 안전 P0 — 자격증명 미준비 상태에서 앱이 죽지 않도록)
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "pillmate.notification.provider", havingValue = "fcm")
public class LazyFirebaseMessagingProvider implements FirebaseMessagingProvider {

    private static final String FIREBASE_APP_NAME = "pillmate-fcm";

    private final String serviceAccountJson;
    private final String credentialsPath;

    private volatile boolean initialized = false;
    private volatile FirebaseMessaging messaging = null;

    public LazyFirebaseMessagingProvider(
            @Value("${FIREBASE_SERVICE_ACCOUNT_JSON:}") String serviceAccountJson,
            @Value("${FIREBASE_CREDENTIALS_PATH:}") String credentialsPath) {
        this.serviceAccountJson = serviceAccountJson;
        this.credentialsPath = credentialsPath;
    }

    @Override
    public Optional<FirebaseMessaging> get() {
        if (!initialized) {
            initOnce();
        }
        return Optional.ofNullable(messaging);
    }

    private synchronized void initOnce() {
        if (initialized) {
            return;
        }
        initialized = true;
        messaging = tryInitialize();
    }

    private FirebaseMessaging tryInitialize() {
        try (InputStream credentialStream = openCredentialStream()) {
            if (credentialStream == null) {
                log.warn("[FCM] 자격증명 미설정 (FIREBASE_SERVICE_ACCOUNT_JSON / FIREBASE_CREDENTIALS_PATH) — FCM 발송 비활성, graceful skip");
                return null;
            }
            FirebaseApp app = resolveFirebaseApp(credentialStream);
            log.info("[FCM] FirebaseApp 초기화 완료 name={}", app.getName());
            return FirebaseMessaging.getInstance(app);
        } catch (Exception e) {
            log.warn("[FCM] FirebaseApp 초기화 실패 — FCM 발송 비활성, graceful skip reason={}", e.getMessage());
            return null;
        }
    }

    private FirebaseApp resolveFirebaseApp(InputStream credentialStream) throws Exception {
        return FirebaseApp.getApps().stream()
                .filter(app -> FIREBASE_APP_NAME.equals(app.getName()))
                .findFirst()
                .orElseGet(() -> initializeApp(credentialStream));
    }

    private FirebaseApp initializeApp(InputStream credentialStream) {
        try {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(credentialStream))
                    .build();
            return FirebaseApp.initializeApp(options, FIREBASE_APP_NAME);
        } catch (Exception e) {
            throw new IllegalStateException("FirebaseApp 초기화 실패", e);
        }
    }

    private InputStream openCredentialStream() throws Exception {
        if (serviceAccountJson != null && !serviceAccountJson.isBlank()) {
            return new ByteArrayInputStream(serviceAccountJson.getBytes(StandardCharsets.UTF_8));
        }
        if (credentialsPath != null && !credentialsPath.isBlank()) {
            return new FileInputStream(Path.of(credentialsPath).toFile());
        }
        return null;
    }
}
