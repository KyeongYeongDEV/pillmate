-- V6: drugs 테이블에 식약처 3종 API 통합 컬럼 추가
--   1) 낱알식별 (MdcinGrnIdntfcInfoService03)
--   2) 의약품개요(e약은요)  — V3 시점에 일부 컬럼 있음, 신규는 경고/주의/상호작용 텍스트
--   3) 의약품 제품 허가정보 (DrugPrdtPrmsnInfoService07)
--
-- 모든 신규 컬럼은 NULL 허용 (점진 적재 + 일부 itemSeq는 한 API에만 존재).
-- 기존 V3 마이그레이션은 수정하지 않는다 (Flyway 불변성).

-- ──────────────────────────────────────────────────────────────────
-- A. 낱알식별 (외형) 컬럼
-- ──────────────────────────────────────────────────────────────────
ALTER TABLE drugs ADD COLUMN shape_class      VARCHAR(20);   -- DRUG_SHAPE 원형/타원형/장방형 ...
ALTER TABLE drugs ADD COLUMN color_class      VARCHAR(40);   -- COLOR_CLASS1 (+CLASS2 옵션, "흰색,분홍" 같이 합쳐 저장)
ALTER TABLE drugs ADD COLUMN line_front       VARCHAR(20);   -- LINE_FRONT
ALTER TABLE drugs ADD COLUMN line_back        VARCHAR(20);   -- LINE_BACK
ALTER TABLE drugs ADD COLUMN mark_code_front  VARCHAR(60);   -- MARK_CODE_FRONT 또는 PRINT_FRONT
ALTER TABLE drugs ADD COLUMN mark_code_back   VARCHAR(60);   -- MARK_CODE_BACK  또는 PRINT_BACK
ALTER TABLE drugs ADD COLUMN chart            TEXT;          -- CHART (앞/뒷면 형상 자연어)
ALTER TABLE drugs ADD COLUMN item_image       TEXT;          -- ITEM_IMAGE (식약처 CDN URL)

-- ──────────────────────────────────────────────────────────────────
-- B. 제품 허가 (메타) 컬럼
-- ──────────────────────────────────────────────────────────────────
ALTER TABLE drugs ADD COLUMN permit_no        VARCHAR(40);   -- PRDUCT_PRMISN_NO
ALTER TABLE drugs ADD COLUMN permit_date      DATE;          -- ITEM_PERMIT_DATE (YYYYMMDD → DATE)
ALTER TABLE drugs ADD COLUMN cancel_date      DATE;          -- CANCEL_DATE NULL=정상
ALTER TABLE drugs ADD COLUMN is_rare          BOOLEAN NOT NULL DEFAULT FALSE;  -- 희귀의약품 여부
ALTER TABLE drugs ADD COLUMN etc_otc          VARCHAR(20);   -- SPCLTY_PBLC/ETC_OTC_NAME 전문/일반
ALTER TABLE drugs ADD COLUMN storage_method   TEXT;          -- depositMethodQesitm (e약은요)
ALTER TABLE drugs ADD COLUMN package_unit     VARCHAR(200);  -- 포장단위 (상세조회 API에서)
ALTER TABLE drugs ADD COLUMN valid_term       VARCHAR(100);  -- 유효기간 (상세조회 API에서)
ALTER TABLE drugs ADD COLUMN main_ingr        TEXT;          -- ITEM_INGR_NAME 주성분
ALTER TABLE drugs ADD COLUMN narcotic_kind    VARCHAR(40);   -- 마약/향정 구분 (없으면 NULL)

-- ──────────────────────────────────────────────────────────────────
-- C. 부가 (의료 안전성 강화) — e약은요의 경고/주의/상호작용
-- ──────────────────────────────────────────────────────────────────
ALTER TABLE drugs ADD COLUMN warning          TEXT;          -- atpnWarnQesitm (경고)
ALTER TABLE drugs ADD COLUMN precaution       TEXT;          -- atpnQesitm (주의)
ALTER TABLE drugs ADD COLUMN interaction      TEXT;          -- intrcQesitm (상호작용)
ALTER TABLE drugs ADD COLUMN class_name       VARCHAR(100);  -- CLASS_NAME / PRDUCT_TYPE 약효분류
ALTER TABLE drugs ADD COLUMN open_de          DATE;          -- openDe e약은요 공개일
ALTER TABLE drugs ADD COLUMN update_de        DATE;          -- updateDe e약은요 최신 갱신일
ALTER TABLE drugs ADD COLUMN bizrno           VARCHAR(20);   -- 사업자등록번호

-- ──────────────────────────────────────────────────────────────────
-- D. 인덱스
-- ──────────────────────────────────────────────────────────────────
CREATE INDEX idx_drugs_permit_date ON drugs (permit_date) WHERE permit_date IS NOT NULL;
CREATE INDEX idx_drugs_shape       ON drugs (shape_class) WHERE shape_class IS NOT NULL;
CREATE INDEX idx_drugs_color       ON drugs (color_class) WHERE color_class IS NOT NULL;
CREATE INDEX idx_drugs_etc_otc     ON drugs (etc_otc)     WHERE etc_otc     IS NOT NULL;

-- ──────────────────────────────────────────────────────────────────
-- E. 무결성
-- ──────────────────────────────────────────────────────────────────
ALTER TABLE drugs ADD CONSTRAINT chk_drugs_etc_otc
    CHECK (etc_otc IS NULL OR etc_otc IN ('전문의약품','일반의약품'));

-- ──────────────────────────────────────────────────────────────────
-- 메모
-- ──────────────────────────────────────────────────────────────────
-- 1) tsv 컬럼(V3)은 generated stored 라 자동 갱신. main_ingr/ingredient 머지 시 INSERT/UPDATE에서 반영됨.
-- 2) 모든 INSERT는 source='식품의약품안전처' 강제 (V3 default 유지).
-- 3) 머지 우선순위(스크립트 책임): e약은요 > 제품허가 > 낱알식별.
