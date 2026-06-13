# Gate D — 동치 검증 결과

## 핵심 메트릭

| 항목 | legacy cascade | RRF surfacing | RRF AUTO only |
|------|---------------|---------------|---------------|
| Hit@1 (약 표시됨) | 98/100 (98.0%) | 98/100 (98.0%) | 69/100 (69.0%) |
| **⛔ 표시 퇴보** (surfacing MISS) | — | **1건** | — |
| ⚠️ AUTO→MANUAL 격하 | — | — | **28건** |
| **⛔ false-auto** (오확정) | — | — | **0건** |

> **surfacing**: AUTO primary 또는 MANUAL options[0] — 사용자에게 약이 표시됨
> **AUTO only**: primary 확정 건수만 — false-auto 0 조건 대상

## ⛔ 표시 퇴보 케이스 (legacy HIT → RRF top candidate도 MISS)

- `gt_035` (hard): `'콜레스테롤약'` — legacy→`로수바엘정10밀리그램(로수바스타틴칼슘)` / rrf→`콜레스텐연질캡슐`

## ⚠️ AUTO→MANUAL 격하 (약은 보이지만 사용자 확인 필요)

총 28건. 대부분 영어 INN (Tylenol, Aspirin...) 또는 한국어 약종 이름 (진통소염제, 수면제...).

- `gt_021`: `'Tylenol 500'` → surfaced `타이레놀500mg` (MANUAL)
- `gt_024`: `'Aspirin 100mg'` → surfaced `아스피린100mg` (MANUAL)
- `gt_025`: `'Metformin 500'` → surfaced `유한메트포르민염산염정500밀리그램` (MANUAL)
- `gt_031`: `'항히스타민제'` → surfaced `나노텍정(세티리진염산염)` (MANUAL)
- `gt_032`: `'혈압약 5mg'` → surfaced `동화암로디핀베실산염정5밀리그램` (MANUAL)
- ... 외 23건 (건별 비교표 참조)

> 이 케이스들은 RRF BGE 임계(0.70) 미달로 MANUAL 처리. legacy는 threshold 없이 바로 반환.
> **의료 안전 측면: MANUAL이 더 보수적으로 올바른 동작** (사용자 확인으로 오확정 방지).

## 건별 비교표

| GT ID | diff | name_raw | INN | legacy | RRF(surf) | RRF decision | ⛔표시퇴보 | ⚠️AUTO격하 | ⛔false-auto |
|-------|------|----------|-----|--------|-----------|--------------|-----------|-----------|------------|
| gt_001 | easy | `타이레놀정500밀리그램` | `타이레놀` | ✅ ilike | ✅ exact_fast | AUTO |  |  |  |
| gt_002 | easy | `아목시실린캡슐250밀리그램` | `아목시실` | ✅ ilike | ✅ exact_fast | AUTO |  |  |  |
| gt_003 | easy | `이부프로펜정400밀리그램` | `이부프로` | ✅ ilike | ✅ exact_fast | AUTO |  |  |  |
| gt_004 | easy | `아스피린정100밀리그램` | `아스피린` | ✅ ilike | ✅ exact_fast | AUTO |  |  |  |
| gt_005 | easy | `메트포르민염산염정500밀리그램` | `메트포르` | ✅ ilike | ✅ exact_fast | AUTO |  |  |  |
| gt_006 | easy | `암로디핀베실산염정5밀리그램` | `암로디핀` | ✅ ilike | ✅ exact_fast | AUTO |  |  |  |
| gt_007 | easy | `로수바스타틴칼슘정10밀리그램` | `로수바스` | ✅ ilike | ✅ exact_fast | AUTO |  |  |  |
| gt_008 | easy | `오메프라졸장용캡슐20밀리그램` | `오메프라` | ✅ prefix_relaxed | ✅ exact_fast | AUTO |  |  |  |
| gt_009 | easy | `세티리진염산염정10밀리그램` | `세티리진` | ✅ ilike | ✅ exact_fast | AUTO |  |  |  |
| gt_010 | easy | `클래리스로마이신정500밀리그램` | `클래리스` | ✅ ilike | ✅ exact_fast | AUTO |  |  |  |
| gt_011 | easy | `독시사이클린염산염캡슐100밀리그램` | `독시사이` | ✅ prefix_relaxed | ✅ exact_fast | AUTO |  |  |  |
| gt_012 | easy | `프레드니솔론정5밀리그램` | `프레드니` | ✅ ilike | ✅ exact_fast | AUTO |  |  |  |
| gt_013 | easy | `디클로페낙나트륨장용정50밀리그램` | `디클로페` | ✅ prefix_relaxed | ✅ exact_fast | AUTO |  |  |  |
| gt_014 | easy | `레보플록사신정500밀리그램` | `레보플록` | ✅ ilike | ✅ exact_fast | AUTO |  |  |  |
| gt_015 | easy | `에소메프라졸마그네슘삼수화물장용캡슐40` | `에소메프` | ✅ prefix_relaxed | ✅ exact_fast | AUTO |  |  |  |
| gt_016 | medium | `리오노필정` | `리오노필` | ❌ none | ❌ rrf | MANUAL |  |  |  |
| gt_017 | medium | `타이레놀 500` | `타이레놀` | ✅ token | ✅ exact_fast | AUTO |  |  |  |
| gt_018 | medium | `아목시` | `아목시` | ✅ ilike | ✅ exact_fast | AUTO |  |  |  |
| gt_019 | medium | `메트포르민 500mg` | `메트포르민` | ✅ ilike | ✅ exact_fast | AUTO |  |  |  |
| gt_020 | medium | `암로디핀5` | `암로디핀` | ✅ token | ✅ exact_fast | AUTO |  |  |  |
| gt_021 | medium | `Tylenol 500` | `타이레놀` | ✅ ingredient_en | ✅ rrf | MANUAL |  | ⚠️ |  |
| gt_022 | medium | `Amoxicillin 250` | `아목시실린` | ✅ ingredient_en | ✅ exact_fast | AUTO |  |  |  |
| gt_023 | medium | `Ibuprofen 400mg` | `이부프로` | ✅ ilike | ✅ exact_fast | AUTO |  |  |  |
| gt_024 | medium | `Aspirin 100mg` | `아스피린` | ✅ ingredient_en | ✅ rrf | MANUAL |  | ⚠️ |  |
| gt_025 | medium | `Metformin 500` | `메트포르민` | ✅ ingredient_en | ✅ rrf | MANUAL |  | ⚠️ |  |
| gt_026 | easy | `로바스타틴정20밀리그램` | `로바스타` | ✅ ilike | ✅ exact_fast | AUTO |  |  |  |
| gt_027 | easy | `글리메피라이드정2밀리그램` | `글리메피` | ✅ prefix_relaxed | ✅ exact_fast | AUTO |  |  |  |
| gt_028 | easy | `모사프리드구연산염정5밀리그램` | `모사프리` | ✅ prefix_relaxed | ✅ exact_fast | AUTO |  |  |  |
| gt_029 | easy | `에날라프릴말레산염정5밀리그램` | `에날라프` | ✅ ilike | ✅ exact_fast | AUTO |  |  |  |
| gt_030 | easy | `아테놀롤정50밀리그램` | `아테놀롤` | ✅ ilike | ✅ exact_fast | AUTO |  |  |  |
| gt_031 | hard | `항히스타민제` | `세티리진` | ✅ ingredient_ko | ✅ rrf | MANUAL |  | ⚠️ |  |
| gt_032 | hard | `혈압약 5mg` | `암로디핀` | ✅ ingredient_ko | ✅ rrf | MANUAL |  | ⚠️ |  |
| gt_033 | hard | `위장약` | `오메프라졸` | ✅ ingredient_ko | ✅ rrf | MANUAL |  | ⚠️ |  |
| gt_034 | hard | `당뇨약 500` | `메트포르민` | ✅ ingredient_ko | ✅ rrf | MANUAL |  | ⚠️ |  |
| gt_035 | hard | `콜레스테롤약` | `로수바스타틴` | ✅ ingredient_ko | ❌ rrf | MANUAL | ⛔ |  |  |
| gt_036 | medium | `타이레늘정500` | `타이레놀` | ✅ prefix_relaxed | ✅ rrf | MANUAL |  | ⚠️ |  |
| gt_037 | medium | `아목시심린캡슐` | `아목시` | ✅ prefix_relaxed | ✅ rrf | MANUAL |  | ⚠️ |  |
| gt_038 | medium | `이부프로픈정` | `이부프로` | ✅ prefix_relaxed | ✅ exact_fast | AUTO |  |  |  |
| gt_039 | medium | `아스피링정` | `아스피린` | ❌ prefix_relaxed | ✅ rrf | MANUAL |  |  |  |
| gt_040 | medium | `메트포르밍정` | `메트포르민` | ✅ prefix_relaxed | ✅ exact_fast | AUTO |  |  |  |
| gt_041 | easy | `세파드록실캡슐250밀리그램` | `세파드록` | ✅ ilike | ✅ exact_fast | AUTO |  |  |  |
| gt_042 | easy | `세파클러캡슐250밀리그램` | `세파클러` | ✅ ilike | ✅ exact_fast | AUTO |  |  |  |
| gt_043 | easy | `독실아민숙시산염정12.5밀리그램` | `독실아민` | ✅ prefix_relaxed | ✅ exact_fast | AUTO |  |  |  |
| gt_044 | easy | `마그네슘산화물정250밀리그램` | `마그네슘` | ✅ prefix_relaxed | ✅ exact_fast | AUTO |  |  |  |
| gt_045 | easy | `에페드린염산염정25밀리그램` | `에페드린` | ✅ ilike | ✅ exact_fast | AUTO |  |  |  |
| gt_046 | easy | `가바펜틴캡슐100밀리그램` | `가바펜틴` | ✅ ilike | ✅ exact_fast | AUTO |  |  |  |
| gt_047 | easy | `졸피뎀타르타르산염정10밀리그램` | `졸피뎀` | ✅ prefix_relaxed | ✅ exact_fast | AUTO |  |  |  |
| gt_048 | easy | `로페라미드염산염캡슐2밀리그램` | `로페라미` | ✅ ilike | ✅ exact_fast | AUTO |  |  |  |
| gt_049 | easy | `디아제팜정2밀리그램` | `디아제팜` | ✅ ilike | ✅ exact_fast | AUTO |  |  |  |
| gt_050 | easy | `부스코판당의정10밀리그램` | `부스코판` | ✅ ilike | ✅ exact_fast | AUTO |  |  |  |
| gt_051 | medium | `타이레놀` | `타이레놀` | ✅ ilike | ✅ exact_fast | AUTO |  |  |  |
| gt_052 | medium | `아목시실린` | `아목시실린` | ✅ ilike | ✅ exact_fast | AUTO |  |  |  |
| gt_053 | medium | `이부프로펜` | `이부프로펜` | ✅ ilike | ✅ exact_fast | AUTO |  |  |  |
| gt_054 | medium | `아스피린` | `아스피린` | ✅ ilike | ✅ exact_fast | AUTO |  |  |  |
| gt_055 | medium | `메트포르민` | `메트포르민` | ✅ ilike | ✅ exact_fast | AUTO |  |  |  |
| gt_056 | medium | `암로디핀` | `암로디핀` | ✅ ilike | ✅ exact_fast | AUTO |  |  |  |
| gt_057 | medium | `로수바스타틴` | `로수바스타틴` | ✅ ilike | ✅ exact_fast | AUTO |  |  |  |
| gt_058 | medium | `오메프라졸` | `오메프라졸` | ✅ ilike | ✅ exact_fast | AUTO |  |  |  |
| gt_059 | medium | `세티리진` | `세티리진` | ✅ ilike | ✅ exact_fast | AUTO |  |  |  |
| gt_060 | medium | `클래리스로마이신` | `클래리스로마이신` | ✅ ilike | ✅ exact_fast | AUTO |  |  |  |
| gt_061 | medium | `독시사이클린` | `독시사이클린` | ✅ ilike | ✅ exact_fast | AUTO |  |  |  |
| gt_062 | medium | `프레드니솔론` | `프레드니솔론` | ✅ ilike | ✅ exact_fast | AUTO |  |  |  |
| gt_063 | medium | `레보플록사신` | `레보플록사신` | ✅ ilike | ✅ exact_fast | AUTO |  |  |  |
| gt_064 | medium | `에소메프라졸` | `에소메프라졸` | ✅ ilike | ✅ exact_fast | AUTO |  |  |  |
| gt_065 | medium | `세파드록실` | `세파드록실` | ✅ ilike | ✅ exact_fast | AUTO |  |  |  |
| gt_066 | medium | `가바펜틴` | `가바펜틴` | ✅ ilike | ✅ exact_fast | AUTO |  |  |  |
| gt_067 | medium | `졸피뎀` | `졸피뎀` | ✅ ilike | ✅ exact_fast | AUTO |  |  |  |
| gt_068 | medium | `로페라미드` | `로페라미드` | ✅ ilike | ✅ exact_fast | AUTO |  |  |  |
| gt_069 | medium | `글리메피라이드` | `글리메피` | ✅ prefix_relaxed | ✅ exact_fast | AUTO |  |  |  |
| gt_070 | medium | `모사프리드` | `모사프리드` | ✅ ilike | ✅ exact_fast | AUTO |  |  |  |
| gt_071 | hard | `Tylenol` | `타이레놀` | ✅ ingredient_en | ✅ rrf | MANUAL |  | ⚠️ |  |
| gt_072 | hard | `Amoxicillin` | `아목시실린` | ✅ ilike | ✅ exact_fast | AUTO |  |  |  |
| gt_073 | hard | `Ibuprofen` | `이부프로펜` | ✅ ilike | ✅ exact_fast | AUTO |  |  |  |
| gt_074 | hard | `Aspirin` | `아스피린` | ✅ ingredient_en | ✅ rrf | MANUAL |  | ⚠️ |  |
| gt_075 | hard | `Metformin` | `메트포르민` | ✅ ingredient_en | ✅ rrf | MANUAL |  | ⚠️ |  |
| gt_076 | hard | `Amlodipine` | `암로디핀` | ✅ ingredient_en | ✅ rrf | MANUAL |  | ⚠️ |  |
| gt_077 | hard | `Rosuvastatin` | `로수바스타틴` | ✅ ingredient_en | ✅ rrf | MANUAL |  | ⚠️ |  |
| gt_078 | hard | `Omeprazole` | `오메프라졸` | ✅ ingredient_en | ✅ rrf | MANUAL |  | ⚠️ |  |
| gt_079 | hard | `Cetirizine` | `세티리진` | ✅ ingredient_en | ✅ rrf | MANUAL |  | ⚠️ |  |
| gt_080 | hard | `Clarithromycin` | `클래리스로마이신` | ✅ ingredient_en | ✅ rrf | MANUAL |  | ⚠️ |  |
| gt_081 | medium | `타이레놀정500밀그램` | `타이레놀` | ✅ ingredient_ko | ✅ exact_fast | AUTO |  |  |  |
| gt_082 | medium | `아목시실린캡슬` | `아목시실린` | ✅ prefix_relaxed | ✅ exact_fast | AUTO |  |  |  |
| gt_083 | medium | `이부프로렌정` | `이부프로` | ✅ prefix_relaxed | ✅ exact_fast | AUTO |  |  |  |
| gt_084 | medium | `메트포르멘정` | `메트포르민` | ✅ prefix_relaxed | ✅ exact_fast | AUTO |  |  |  |
| gt_085 | medium | `오메프라졸 20mg` | `오메프라졸` | ✅ ilike | ✅ exact_fast | AUTO |  |  |  |
| gt_086 | hard | `Gabapentin` | `가바펜틴` | ✅ ingredient_en | ✅ rrf | MANUAL |  | ⚠️ |  |
| gt_087 | hard | `Zolpidem` | `졸피뎀` | ✅ ingredient_en | ✅ rrf | MANUAL |  | ⚠️ |  |
| gt_088 | hard | `Loperamide` | `로페라미드` | ✅ ingredient_en | ✅ rrf | MANUAL |  | ⚠️ |  |
| gt_089 | hard | `Glimepiride` | `글리메피리` | ✅ ingredient_en | ✅ rrf | MANUAL |  | ⚠️ |  |
| gt_090 | hard | `Mosapride` | `모사프리드` | ✅ ingredient_en | ✅ rrf | MANUAL |  | ⚠️ |  |
| gt_091 | medium | `레보플록사씬정` | `레보플록사신` | ✅ prefix_relaxed | ✅ exact_fast | AUTO |  |  |  |
| gt_092 | hard | `에소메프라조를` | `에소메프라` | ✅ prefix_relaxed | ✅ exact_fast | AUTO |  |  |  |
| gt_093 | medium | `로수바스타팅정` | `로수바스타틴` | ✅ prefix_relaxed | ✅ exact_fast | AUTO |  |  |  |
| gt_094 | medium | `세파클롤캡슐` | `세파클` | ✅ prefix_relaxed | ✅ rrf | MANUAL |  | ⚠️ |  |
| gt_095 | medium | `글리메피래이드정` | `글리메피` | ✅ prefix_relaxed | ✅ exact_fast | AUTO |  |  |  |
| gt_096 | hard | `진통소염제` | `이부프로펜` | ✅ ingredient_ko | ✅ rrf | MANUAL |  | ⚠️ |  |
| gt_097 | hard | `수면제` | `졸피뎀` | ✅ ingredient_ko | ✅ rrf | MANUAL |  | ⚠️ |  |
| gt_098 | hard | `항생제캡슐` | `아목시실린` | ✅ ingredient_ko | ✅ rrf | MANUAL |  | ⚠️ |  |
| gt_099 | hard | `혈당강하제` | `메트포르민` | ✅ ingredient_ko | ✅ rrf | MANUAL |  | ⚠️ |  |
| gt_100 | hard | `항생체` | `아목시실린` | ✅ ingredient_ko | ✅ rrf | MANUAL |  | ⚠️ |  |

## 평가=운영 단일 경로 확인

- `run_eval_full.py` 가 `rrf_factory.build_rrf_matcher_inner()` 를 호출 ✅
- `main.py` 가 `rrf_factory.build_rrf_matcher()` → `build_rrf_matcher_inner()` 를 호출 ✅
- 두 경로가 동일 `RrfMatcher` 구성을 사용 — eval≠prod 괴리 제거 ✅
- `_cascade_search` (구 평가 전용 경로) 제거 완료 ✅