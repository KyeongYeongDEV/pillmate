-- T-BE-MED-SHARE-GRANT: 알약 정보(L2) 공유 권한 — owner 가 케어그룹 안에서 특정 viewer 에게
-- 알약 이름/용법 등 상세 정보 열람을 명시적으로 허용한 관계를 저장.
-- L1(복약 여부)은 기존 CareGroupGuard.requirePatientAccessible 로 이미 공개 — 본 테이블은 L2 전용.
-- CREATE TABLE/INDEX 신규만 (db-safety: DROP/DELETE/기존 마이그레이션 수정 없음)

CREATE TABLE medication_share_grants (
    id             BIGSERIAL PRIMARY KEY,
    care_group_id  BIGINT NOT NULL REFERENCES care_groups(id),
    owner_user_id  BIGINT NOT NULL REFERENCES users(id),
    viewer_user_id BIGINT NOT NULL REFERENCES users(id),
    granted_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_medication_share_grants_group_owner_viewer
        UNIQUE (care_group_id, owner_user_id, viewer_user_id)
);

-- L2 판정 조회 (owner, viewer 기준 — canViewMedicationDetail)
CREATE INDEX idx_medication_share_grants_owner_viewer
    ON medication_share_grants (owner_user_id, viewer_user_id);

-- 공유 설정 화면 조회 (그룹 안 특정 owner 의 공유 목록)
CREATE INDEX idx_medication_share_grants_group_owner
    ON medication_share_grants (care_group_id, owner_user_id);
