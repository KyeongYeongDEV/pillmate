-- 약물 유발 영양소 고갈(DIND) 매핑 테이블 — 근거 있는 항목만 수록
CREATE TABLE drug_nutrient_depletion (
    id             BIGSERIAL    PRIMARY KEY,
    ingredient     VARCHAR(100) NOT NULL,
    nutrient       VARCHAR(100) NOT NULL,
    evidence_level VARCHAR(20)  NOT NULL,
    advice         TEXT         NOT NULL,
    source         VARCHAR(300) NOT NULL
);

CREATE INDEX idx_drug_nutrient_depletion_ingredient ON drug_nutrient_depletion (ingredient);

-- ============================================================
-- 시드: 근거 강한 항목만 (HIGH/MODERATE). 출처 표기 필수.
-- ★'복용하세요' 지시 금지 — '영향을 줄 수 있어요 + 상담' 톤
-- ============================================================

-- Metformin → 비타민 B12 (HIGH: Reinstatler 2012, Diabetes Care; 식약처)
INSERT INTO drug_nutrient_depletion (ingredient, nutrient, evidence_level, advice, source)
VALUES ('메트포르민', '비타민 B12', 'HIGH',
        '장기 복용 시 비타민 B12 흡수에 영향을 줄 수 있어요. 정기적인 혈중 농도 확인 및 보충 방법은 약사와 상담하세요.',
        '식품의약품안전처 의약품정보; Reinstatler et al., Diabetes Care 2012');

-- Statin 계열 → CoQ10 (MODERATE: Langsjoen 2005, BioFactors; 약학정보원)
INSERT INTO drug_nutrient_depletion (ingredient, nutrient, evidence_level, advice, source)
VALUES ('아토르바스타틴', '코엔자임 Q10', 'MODERATE',
        '스타틴 계열 약물은 체내 코엔자임 Q10 합성에 영향을 줄 수 있어요. 보충 여부는 약사 또는 의사와 상담하세요.',
        '약학정보원; Langsjoen et al., BioFactors 2005');

INSERT INTO drug_nutrient_depletion (ingredient, nutrient, evidence_level, advice, source)
VALUES ('로수바스타틴', '코엔자임 Q10', 'MODERATE',
        '스타틴 계열 약물은 체내 코엔자임 Q10 합성에 영향을 줄 수 있어요. 보충 여부는 약사 또는 의사와 상담하세요.',
        '약학정보원; Langsjoen et al., BioFactors 2005');

INSERT INTO drug_nutrient_depletion (ingredient, nutrient, evidence_level, advice, source)
VALUES ('심바스타틴', '코엔자임 Q10', 'MODERATE',
        '스타틴 계열 약물은 체내 코엔자임 Q10 합성에 영향을 줄 수 있어요. 보충 여부는 약사 또는 의사와 상담하세요.',
        '약학정보원; Langsjoen et al., BioFactors 2005');

-- 이뇨제(Loop) → 마그네슘·칼륨 (HIGH: Quamme 1997, Kidney Int; 식약처)
INSERT INTO drug_nutrient_depletion (ingredient, nutrient, evidence_level, advice, source)
VALUES ('푸로세미드', '마그네슘', 'HIGH',
        '이뇨제 장기 복용 시 마그네슘 배설이 증가할 수 있어요. 혈중 수치 확인 및 보충 방법은 약사와 상담하세요.',
        '식품의약품안전처 의약품정보; Quamme, Kidney Int 1997');

INSERT INTO drug_nutrient_depletion (ingredient, nutrient, evidence_level, advice, source)
VALUES ('푸로세미드', '칼륨', 'HIGH',
        '이뇨제 장기 복용 시 칼륨 배설이 증가할 수 있어요. 정기적인 혈중 수치 확인 및 보충 방법은 약사와 상담하세요.',
        '식품의약품안전처 의약품정보; 이뇨제 처방 지침');

-- 이뇨제(Thiazide) → 마그네슘·칼륨 (HIGH: Quamme 1997; 식약처)
INSERT INTO drug_nutrient_depletion (ingredient, nutrient, evidence_level, advice, source)
VALUES ('히드로클로로티아지드', '마그네슘', 'HIGH',
        '이뇨제 장기 복용 시 마그네슘 배설이 증가할 수 있어요. 혈중 수치 확인 및 보충 방법은 약사와 상담하세요.',
        '식품의약품안전처 의약품정보; Quamme, Kidney Int 1997');

INSERT INTO drug_nutrient_depletion (ingredient, nutrient, evidence_level, advice, source)
VALUES ('히드로클로로티아지드', '칼륨', 'HIGH',
        '이뇨제 장기 복용 시 칼륨 배설이 증가할 수 있어요. 정기적인 혈중 수치 확인 및 보충 방법은 약사와 상담하세요.',
        '식품의약품안전처 의약품정보; 이뇨제 처방 지침');

-- PPI 계열 → 비타민 B12·마그네슘 (HIGH: Lam 2013, JAMA; FDA 2011; 식약처)
INSERT INTO drug_nutrient_depletion (ingredient, nutrient, evidence_level, advice, source)
VALUES ('오메프라졸', '비타민 B12', 'HIGH',
        'PPI 장기 복용 시 위산 억제로 비타민 B12 흡수에 영향을 줄 수 있어요. 복용 기간과 보충 방법은 약사와 상담하세요.',
        '식품의약품안전처 의약품정보; Lam et al., JAMA 2013');

INSERT INTO drug_nutrient_depletion (ingredient, nutrient, evidence_level, advice, source)
VALUES ('오메프라졸', '마그네슘', 'HIGH',
        'PPI 장기 복용 시 마그네슘 저하와 관련이 있을 수 있어요. 정기적인 혈중 수치 확인을 권장하며, 약사와 상담하세요.',
        '식품의약품안전처 의약품정보; FDA Drug Safety Communication 2011');

INSERT INTO drug_nutrient_depletion (ingredient, nutrient, evidence_level, advice, source)
VALUES ('에소메프라졸', '비타민 B12', 'HIGH',
        'PPI 장기 복용 시 위산 억제로 비타민 B12 흡수에 영향을 줄 수 있어요. 복용 기간과 보충 방법은 약사와 상담하세요.',
        '식품의약품안전처 의약품정보; Lam et al., JAMA 2013');

INSERT INTO drug_nutrient_depletion (ingredient, nutrient, evidence_level, advice, source)
VALUES ('에소메프라졸', '마그네슘', 'HIGH',
        'PPI 장기 복용 시 마그네슘 저하와 관련이 있을 수 있어요. 정기적인 혈중 수치 확인을 권장하며, 약사와 상담하세요.',
        '식품의약품안전처 의약품정보; FDA Drug Safety Communication 2011');

INSERT INTO drug_nutrient_depletion (ingredient, nutrient, evidence_level, advice, source)
VALUES ('란소프라졸', '비타민 B12', 'HIGH',
        'PPI 장기 복용 시 위산 억제로 비타민 B12 흡수에 영향을 줄 수 있어요. 복용 기간과 보충 방법은 약사와 상담하세요.',
        '식품의약품안전처 의약품정보; Lam et al., JAMA 2013');

INSERT INTO drug_nutrient_depletion (ingredient, nutrient, evidence_level, advice, source)
VALUES ('란소프라졸', '마그네슘', 'HIGH',
        'PPI 장기 복용 시 마그네슘 저하와 관련이 있을 수 있어요. 정기적인 혈중 수치 확인을 권장하며, 약사와 상담하세요.',
        '식품의약품안전처 의약품정보; FDA Drug Safety Communication 2011');

-- 경구피임약(에스트로겐 함유) → 비타민 B6·엽산 (MODERATE: Lussana 2003; 식약처)
INSERT INTO drug_nutrient_depletion (ingredient, nutrient, evidence_level, advice, source)
VALUES ('에티닐에스트라디올', '비타민 B6', 'MODERATE',
        '에스트로겐 함유 경구피임약은 비타민 B6 대사에 영향을 줄 수 있어요. 영양 상태 및 보충 방법은 약사와 상담하세요.',
        '약학정보원; Lussana et al., Thromb Haemost 2003');

INSERT INTO drug_nutrient_depletion (ingredient, nutrient, evidence_level, advice, source)
VALUES ('에티닐에스트라디올', '엽산', 'MODERATE',
        '에스트로겐 함유 경구피임약은 엽산 대사에 영향을 줄 수 있어요. 특히 임신을 계획 중이라면 약사 또는 의사와 상담하세요.',
        '식품의약품안전처 의약품정보; Lussana et al., Thromb Haemost 2003');
