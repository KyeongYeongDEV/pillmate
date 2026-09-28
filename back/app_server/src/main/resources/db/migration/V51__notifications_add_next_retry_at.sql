-- Polling Outbox 재시도 스위퍼에 exponential backoff + jitter 도입(2026-09-27).
-- 재시도 실패 시 다음 시도 가능 시각을 이 컬럼에 기록해, FCM 장애 시 60s마다 몰아치지 않고
-- 점증 간격(1→2→4분 계열) + jitter 로 분산한다. NULL = 아직 재시도 안 한 최초 stuck 대상.
ALTER TABLE notifications ADD COLUMN next_retry_at TIMESTAMPTZ;
