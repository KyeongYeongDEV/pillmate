-- T-BE-DOSE-REMINDER-PUSH (2026-07-13): 복약 시간 도래 시 환자 본인 리마인더 푸시.
-- 데이터 삭제/변경 없음 — 컬럼 추가 + 제약 완화 + CHECK 타입 확장(재생성) + 부분 인덱스.

ALTER TABLE dose_logs ADD COLUMN reminded_at TIMESTAMPTZ;

-- 솔로(케어그룹 없는) 사용자 리마인더 저장 허용 — schedules/prescriptions 선례(V41/V17) 동일
ALTER TABLE notifications ALTER COLUMN care_group_id DROP NOT NULL;

-- 타입 목록 확장을 위한 CHECK 재생성 (데이터 영향 없음, spec 명시)
ALTER TABLE notifications DROP CONSTRAINT chk_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT chk_notifications_type CHECK (
    type IN ('DOSE_REMINDER','DOSE_TAKEN','DOSE_MISSED','DOSE_CANCELED',
             'DDI_CRITICAL','PRESCRIPTION_NEW','WEEKLY_REPORT','GROUP_MEMBER_JOINED'));

-- 리마인더 폴러 조회용 부분 인덱스 (월 900만 건 대비)
CREATE INDEX idx_dose_logs_reminder_due ON dose_logs (scheduled_at)
    WHERE status = 'PENDING' AND reminded_at IS NULL;
