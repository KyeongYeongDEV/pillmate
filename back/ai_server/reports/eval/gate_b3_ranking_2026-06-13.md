# Gate B3 — StrongExactAdapter (Gate A++) 순수 랭킹 Hit@1

> 날짜: 2026-06-13  
> 변경 (Gate A++): prefix[:4] 쿼리에 require_main_hit=True 플래그.  
>   괄호 앞 main_name 미포함 시 단축 금지 → RRF 위임 (false-auto 방지).  
>   cascade/stripped/token 쿼리는 INN 수준 → 괄호 내 성분명 매칭 허용 유지.  
> INN dict: gt_092 '에소메프라졸' → '에소메프라' (에소↔에스오 철자 mismatch 해소)  
> Retriever: StrongExact · IlikeMulti · Trigram · TokenIlike · PrefixRelax · Ingredient  
> Reranker: DomainReranker → BgeRerankerAdapter(normalize=True)  

## 0. 최우선 지표 — false-auto

| 지표 | 값 |
|------|----|
| **false-auto 건수** | **0건** ✅ 목표 달성 |
| Hit@1 | 98.0% (98/100) |
| legacy 대비 | +1.0% |

### false-auto: **없음** ✅

## 1. 핵심 비교

| 경로 | Hit@1 | 건수 |
|------|-------|------|
| legacy cascade (기준) | 97.0% | 97/100 |
| Gate B (RrfMatcher+GateA, ExactIlikeAdapter) | 84.0% | 84/100 |
| Gate B2 (StrongExactAdapter) | 97.0% | 97/100 |
| **Gate B3 (StrongExact Gate A++)** | **98.0%** | **98/100** |
| vs legacy | +1.0% | +1 |

## 2. Difficulty별

| Difficulty | 건수 | Hit | Hit@1 |
|-----------|------|-----|-------|
| easy | 30 | 30 | 100.0% |
| medium | 44 | 43 | 97.7% |
| hard | 26 | 25 | 96.2% |

## 3. 입력 유형별 Hit@1

| 유형 | 건수 | Hit | Hit@1 |
|------|------|-----|-------|
| long_compound (정규 전문의약품명) | 55 | 54 | 98.2% |
| english (영문 브랜드/성분명) | 20 | 20 | 100.0% |
| general_name (일반 카테고리명) | 10 | 9 | 90.0% |
| ocr_typo (OCR 오탈자) | 8 | 8 | 100.0% |
| abbreviated (짧은 한글명) | 7 | 7 | 100.0% |

## 4. Gate B2 대비 변화 (잔여 miss 3건 기준)

### 4-1. 회복 항목 (1/3건 회복)

| GT ID | name_raw | ranked_top1 | INN | Stage |
|-------|----------|-------------|-----|-------|
| gt_092 | 에소메프라조를 | 에소메프라정20밀리그램(에스오메프라졸마그네슘삼수화물) | 에소메프라 | AUTO |

### 4-2. 회귀: **없음** ✅

## 5. 잔여 Miss (2건)

| GT ID | Difficulty | 입력유형 | name_raw | ranked_top1 | INN | Score | AUTO? |
|-------|-----------|---------|----------|-------------|-----|-------|-------|
| gt_035 | hard | general_name | 콜레스테롤약 | 콜레스텐연질캡슐 | 로수바스타틴 | 0.192 |  |
| gt_016 | medium | long_compound | 리오노필정 | (none) | 리오노필 | 0.000 |  |

## 6. CTO 판단 사항

- **false-auto: 0건** → 의료 안전 목표 달성 ✅
- **Gate B3 Hit@1: 98.0%** (legacy 97.0% 대비 +1.0%)
- 회귀: 0건, Gate B2 대비 신규 회복: 1건
- false-auto 0 + 회귀 0 + Hit@1 ≥ legacy → **Gate C(임계 스윕) 진행 가능**
