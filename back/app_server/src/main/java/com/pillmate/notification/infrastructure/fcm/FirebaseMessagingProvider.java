package com.pillmate.notification.infrastructure.fcm;

import com.google.firebase.messaging.FirebaseMessaging;

import java.util.Optional;

/**
 * FirebaseMessaging 획득 seam. 자격증명 미존재 시 빈 Optional 로 graceful skip 보장.
 */
public interface FirebaseMessagingProvider {
    Optional<FirebaseMessaging> get();
}
