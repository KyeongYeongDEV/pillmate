# Spec — OCR 매칭 연구 로그 (ocr_match_logs)

> 목적: OCR 약품명 매칭을 연구용으로 DB 기록. 사용자 화면엔 신뢰도 % 미노출(별도 FE 작업 완료).
> 원칙: best-effort(매칭/OCR 흐름 막지 않음), ★환자 식별정보·이미지 원본 저장 금지(비식별), 단순 테이블 1개(오버엔지니어링 회피).

## 테이블 (Flyway V33, app_server, additive only)
`ocr_match_logs`:
- `id` BIGSERIAL PK, `created_at` TIMESTAMPTZ default now()
- `image_hash` VARCHAR(64)  — 원본 이미지 SHA-256 (이미지 자체 저장 X). 같은 이미지의 약들 묶기 + app_server 정정 연결 키
- `raw_ocr_text` VARCHAR(300) NOT NULL — OCR 인식 원문 약명
- `normalized_query` VARCHAR(300) — 전처리(자모/정제) 후 실제 검색어
- `matched_kd_code` VARCHAR(40), `matched_drug_name` VARCHAR(300) — 결과(없으면 null)
- `decision` VARCHAR(20) — MATCHED / UNMATCHED / REVIEW
- `final_score` NUMERIC(5,4) — 최종 점수(0~1)
- `rrf_score` NUMERIC(8,5), `reranker_score` NUMERIC(8,5) — 단계별(nullable)
- `surfaced_by` VARCHAR(120) — 어느 retriever가 띄웠나(exact/ilike/trigram/token/prefix/ingredient, csv)
- `candidates_json` JSONB — top-N 후보 + 각 점수 [{kdCode,name,score,rank},...]
- `matcher_version` VARCHAR(40), `threshold` NUMERIC(4,3) — 알고리즘/임계값(옛 로그 해석용)
- `gemini_raw_json` JSONB — 추출 용량/횟수/기간 원본(nullable)
- `latency_ms` INT
- `user_corrected_kd_code` VARCHAR(40) — ★사용자 정정 결과(나중 채움, null) = ground truth
- 인덱스: `idx_ocr_match_logs_image_hash (image_hash)`, `idx_ocr_match_logs_created_at`
- ★DROP/DELETE/TRUNCATE 금지(db-safety), 기존 V1~V32 수정 금지

## ai_server — 매칭 직후 로깅 (RrfMatcher 경로)
- 매칭 결정(MatchDecider) 직후, 약품별 1행 ★best-effort 비동기 INSERT(asyncpg). 실패해도 OCR 응답 막지 말 것(try/except 무시 + 경고 로그).
- `image_hash` = OCR 대상 이미지 bytes의 SHA-256 (ai_server가 이미 bytes 보유).
- candidates_json = RrfMatcher 융합 후 top-N(예 5) 후보·점수. surfaced_by = 후보를 surfacing한 adapter들.
- matcher_version/threshold = 현재 RrfMatcher 설정값(ABS_THRESHOLD 등).
- ★환자정보 로깅 금지 — 약품명/점수/해시만. (전체 처방전 텍스트·환자명 X)
- 테이블은 app_server Flyway가 생성(공유 DB) — ai_server는 INSERT만. 부재 시 graceful skip.

## app_server — 사용자 정정 시 update
- 후보 resolve(사용자가 매칭 정정) 유스케이스에서, 정정된 약의 `image_hash`(app_server가 동일 이미지 bytes로 SHA-256 동일 계산) + `raw_ocr_text`로
  `UPDATE ocr_match_logs SET user_corrected_kd_code=? WHERE image_hash=? AND raw_ocr_text=? AND user_corrected_kd_code IS NULL ORDER BY created_at DESC LIMIT 1` (best-effort, 못 찾으면 skip).
- 이 경로는 read/update만 — DELETE 금지.

## 테스트
- ai_server: 매칭 후 INSERT 호출(모킹 DB)·실패 시 OCR 정상 반환 유지. 환자정보 미포함 단언.
- app_server: 정정 시 update 쿼리 호출. V33 마이그레이션 적용.

## 연결
- [[feedback_ocr_improvement_log]] OCR 개선 일지와 연계 — 연구 narrative.
