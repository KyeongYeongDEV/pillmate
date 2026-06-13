# Gate B2 — StrongExactAdapter 순수 랭킹 Hit@1

> 날짜: 2026-06-13  
> 변경: ExactIlikeAdapter → StrongExactAdapter (dose_amount gate 제거 + salt strip + prefix[:4])  
> INN dict: gt_047 '졸피뎀타' → '졸피뎀' (주석산졸피뎀 형태 반영)  
> Retriever: StrongExact · IlikeMulti · Trigram · TokenIlike · PrefixRelax · Ingredient  
> Reranker: DomainReranker → BgeRerankerAdapter(normalize=True)  

## 1. 핵심 비교

| 경로 | Hit@1 | 건수 |
|------|-------|------|
| legacy cascade (기준) | 97.0% | 97/100 |
| Gate B (RrfMatcher+GateA, ExactIlikeAdapter) | 84.0% | 84/100 |
| **Gate B2 (StrongExactAdapter)** | **97.0%** | **97/100** |
| vs legacy | +0.0% | +0 |
| vs Gate B | +13.0% | +13 |

## 2. Difficulty별

| Difficulty | 건수 | Hit | Hit@1 |
|-----------|------|-----|-------|
| easy | 30 | 30 | 100.0% |
| medium | 44 | 43 | 97.7% |
| hard | 26 | 24 | 92.3% |

## 3. 입력 유형별 Hit@1

| 유형 | 건수 | Hit | Hit@1 |
|------|------|-----|-------|
| long_compound | 55 | 53 | 96.4% |
| english | 20 | 20 | 100.0% |
| general_name | 10 | 9 | 90.0% |
| ocr_typo | 8 | 8 | 100.0% |
| abbreviated | 7 | 7 | 100.0% |

## 4. Gate B 대비 변화

### 4-1. 회복 항목 (13/16건 회복)

| GT ID | name_raw | ranked_top1 | INN | Stage |
|-------|----------|-------------|-----|-------|
| gt_002 | 아목시실린캡슐250밀리그램 | 종근당아목시실린캡슐500밀리그램 | 아목시실 | AUTO |
| gt_010 | 클래리스로마이신정500밀리그램 | 클래리스로마이신정200밀리그람[산도즈](수출용) | 클래리스 | AUTO |
| gt_011 | 독시사이클린염산염캡슐100밀리그램 | 영풍독시사이클린정100밀리그램 | 독시사이 | AUTO |
| gt_015 | 에소메프라졸마그네슘삼수화물장용캡슐40밀리그램 | 엠디에소메프라졸정40밀리그램(에스오메프라졸마그네슘이수화물) | 에소메프 | AUTO |
| gt_028 | 모사프리드구연산염정5밀리그램 | 다림모사프리드정5mg(모사프리드시트르산염수화물) | 모사프리 | AUTO |
| gt_038 | 이부프로픈정 | 이부프로딘정400밀리그램(이부프로펜) | 이부프로 | AUTO |
| gt_043 | 독실아민숙시산염정12.5밀리그램 | 슐라폰정(호박산독실아민) | 독실아민 | AUTO |
| gt_044 | 마그네슘산화물정250밀리그램 | 삼천당산화마그네슘정250밀리그람 | 마그네슘 | AUTO |
| gt_047 | 졸피뎀타르타르산염정10밀리그램 | 산도스졸피뎀정10mg(주석산졸피뎀) | 졸피뎀 | AUTO |
| gt_064 | 에소메프라졸 | 엠디에소메프라졸정20밀리그램(에스오메프라졸마그네슘이수화물) | 에소메프라졸 | AUTO |
| gt_068 | 로페라미드 | 지엘로페라미드염산염캡슐 | 로페라미드 | AUTO |
| gt_070 | 모사프리드 | 휴니즈모사프리드시트르산염수화물정 | 모사프리드 | AUTO |
| gt_083 | 이부프로렌정 | 이부프로딘정400밀리그램(이부프로펜) | 이부프로 | AUTO |

### 4-2. ⚠ 회귀: **없음** ✓

## 5. 잔여 Miss (3건)

| GT ID | Difficulty | name_raw | ranked_top1 | INN | Score |
|-------|-----------|----------|-------------|-----|-------|
| gt_035 | hard | 콜레스테롤약 | 아도스테롤(131I)주사액(요오디네이티트(131I)노르 | 로수바스타틴 | 1.000 |
| gt_092 | hard | 에소메프라조를 | 에소메프라정20밀리그램(에스오메프라졸마그네슘삼수화물) | 에소메프라졸 | 1.000 |
| gt_016 | medium | 리오노필정 | (none) | 리오노필 | 0.000 |

## 6. CTO 판단 사항

- **Gate B2 순수 랭킹 Hit@1: 97.0%** (+13.0% vs Gate B)
- vs legacy: +0.0%
- 회귀: 0건, 회복: 13건/16건
- 랭킹 품질 ≥ legacy → **Gate C(임계 스윕) + Gate D(와이어링) 진행 가능**
