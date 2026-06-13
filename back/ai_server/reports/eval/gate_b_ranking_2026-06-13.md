# Gate B — 순수 랭킹 Hit@1 (RrfMatcher + Gate A adapters)

> 날짜: 2026-06-13  
> 지표: ranked[0].name INN 포함 여부 (AUTO/CONFIRM/MANUAL 결정 무관)  
> Retriever: ExactIlikeAdapter · IlikeMultiAdapter · TrigramMultiAdapter  
>            TokenIlikeMultiAdapter · PrefixRelaxMultiAdapter · IngredientMultiAdapter  
> Reranker: DomainReranker → BgeRerankerAdapter(normalize=True, transformers 4.57.6)  
> Hit 판정: _is_hit_by_inn(ranked_top1.name, inn_keyword) — legacy와 동일  
> ABS_THRESHOLD: 사용 안함 (순수 랭킹 지표)  

## 1. 핵심 비교

| 경로 | 지표 | Hit@1 | 건수 |
|------|------|-------|------|
| legacy cascade (run_eval_full.py) | ranked[0] INN | 97.0% | 97/100 |
| **RrfMatcher + Gate A** | **ranked[0] INN** | **84.0%** | **84/100** |
| 차이 | | -13.0% | -13 |

## 2. Difficulty별 분해

| Difficulty | 건수 | Hit | Hit@1 |
|-----------|------|-----|-------|
| easy | 30 | 22 | 73.3% |
| medium | 44 | 38 | 86.4% |
| hard | 26 | 24 | 92.3% |

## 3. 입력 유형별 Hit@1

| 유형 | 건수 | Hit | Hit@1 | 설명 |
|------|------|-----|-------|------|
| long_compound | 55 | 42 | 76.4% | 정규 전문의약품명 (full compound) |
| english | 20 | 20 | 100.0% | 영문 브랜드/성분명 (Tylenol, Amoxicillin) |
| general_name | 10 | 9 | 90.0% | 일반 카테고리명 (혈압약, 위장약) |
| ocr_typo | 8 | 6 | 75.0% | OCR 오탈자 (타이레늘, 아스피링) |
| abbreviated | 7 | 7 | 100.0% | 짧은 한글명 (타이레놀, 아목시) |

## 4. Miss 목록 (16건) — 유형별

### general_name (1건)

| GT ID | Difficulty | name_raw | ranked_top1 | Score |
|-------|-----------|----------|-------------|-------|
| gt_035 | hard | 콜레스테롤약 | 콜레스텐연질캡슐 | 0.192 |

### long_compound (13건)

| GT ID | Difficulty | name_raw | ranked_top1 | Score |
|-------|-----------|----------|-------------|-------|
| gt_002 | easy | 아목시실린캡슐250밀리그램 | 아목시클건조시럽 | -0.139 |
| gt_010 | easy | 클래리스로마이신정500밀리그램 | 클래리마신정500mg(클래리트로마이신) | 0.328 |
| gt_011 | easy | 독시사이클린염산염캡슐100밀리그램 | (none) | 0.000 |
| gt_015 | easy | 에소메프라졸마그네슘삼수화물장용캡슐40밀리그램 | 에소메디정40mg(에스오메프라졸마그네슘이수화물) | 0.186 |
| gt_028 | easy | 모사프리드구연산염정5밀리그램 | 모사프릴정 | 0.193 |
| gt_043 | easy | 독실아민숙시산염정12.5밀리그램 | (none) | 0.000 |
| gt_044 | easy | 마그네슘산화물정250밀리그램 | 마그네스정 | 0.386 |
| gt_047 | easy | 졸피뎀타르타르산염정10밀리그램 | (none) | 0.000 |
| gt_092 | hard | 에소메프라조를 | 에소메드캡슐40밀리그램(에스오메프라졸) | -0.072 |
| gt_016 | medium | 리오노필정 | (none) | 0.000 |
| gt_064 | medium | 에소메프라졸 | 에소메드캡슐40밀리그램(에스오메프라졸) | 0.208 |
| gt_068 | medium | 로페라미드 | 로페라민캡슐 | 0.521 |
| gt_070 | medium | 모사프리드 | 모사프릴정 | 0.589 |

### ocr_typo (2건)

| GT ID | Difficulty | name_raw | ranked_top1 | Score |
|-------|-----------|----------|-------------|-------|
| gt_038 | medium | 이부프로픈정 | 이부프렌드연질캡슐 | 0.261 |
| gt_083 | medium | 이부프로렌정 | 이부프렌드연질캡슐 | 0.274 |

## 5. CTO 판단 사항

- **RrfMatcher 순수 랭킹 Hit@1: 84.0%** vs legacy 97.0% (-13.0%)
- 랭킹 품질 legacy 대비 13.0% 부족 → miss 원인 분석 후 추가 보강 검토

**Miss 원인별 개요:**
- `general_name`: 1건
- `long_compound`: 13건
- `ocr_typo`: 2건
