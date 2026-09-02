package com.pillmate.notification.application.port;

import java.time.Duration;

public interface NudgeCooldownPort {

    // true = 쿨다운 획득 성공(최초 요청, 발송 진행) / false = 이미 쿨다운 중(429)
    boolean tryAcquire(Long doseLogId, Long fromUserId, Duration ttl);

    // 당사자(수신자) 단위 총량 캡 — true = 이 TTL 동안 첫 발송(FCM 진행) / false = 이미 다른 발신자·dose 로 알림 받음(FCM 생략)
    boolean acquireRecipientCap(Long patientId, Duration ttl);
}
