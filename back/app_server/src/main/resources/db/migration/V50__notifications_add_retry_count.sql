-- Polling Outbox 재시도 스위퍼용 — 몇 번 재시도했는지 추적 (2026-09-27 FCM 성능개선/신뢰성)
-- DOSE_REMINDER, DOSE_OVERDUE 등 반드시 발송돼야 하는 알림이 PENDING 에 방치되면
-- 별도 스케줄러가 이 컬럼 기준으로 재시도하고, 한도 초과 시 FAILED 로 전환한다.
ALTER TABLE notifications ADD COLUMN retry_count INT NOT NULL DEFAULT 0;
