# T-OCR-VISION-VARIANT — flash/flash-lite 모델별 별도 파일 + 환경변수 갈아끼우기

작성일: 2026-06-28
사용자 명시: "flash 모델에 맞는 코드는 냅두고 light 모드에 맞는 파일을 생성해서 원하는 대로 갈아끼우는 형식"

## 진단

`back/ai_server/app/rag/ocr/vision.py`의 `RawOcrItem`:
```python
frequency: int        # non-nullable
duration_days: int    # non-nullable
```

→ flash는 항상 정수 채워 반환(통과), **flash-lite는 모르는 필드 `null` 정직 반환 → Pydantic 거부 → OcrParseError → 504**.

## CTO 결정

**Strategy pattern**:
- 기존 `vision.py` = **flash 전용** (그대로 유지, 코드 0 수정)
- 신규 `vision_lite.py` = **flash-lite 전용** (Optional schema + lite 특화 처리)
- main.py 또는 factory에서 `settings.gemini_model` 값으로 두 adapter 중 선택
- 환경변수 `GEMINI_MODEL` 으로 즉시 갈아끼우기 가능

## 절대 규칙

- 기존 vision.py / RawOcrItem schema **무변경** (flash 정확도 유지)
- BE only (ai_server)
- git commit/push 금지 (CTO 단독)
- TDD: 신규 lite adapter 단위 테스트 + 기존 flash 테스트 회귀 0
- clean-code SRP: variant 선택 로직 1곳에서만
- DDD 의존 역전 X
- medical/graceful: source/confidence 강제, fallback 유지
- no-overengineering: 두 variant만 (다른 모델 확장은 별도 task)

---

## BE-Dev 작업

### 1. 신규 파일 (lite 전용)

**`back/ai_server/app/rag/ocr/vision_lite.py`** (NEW)
- 기존 `vision.py`에서 다음만 변경 사항:
  - `RawOcrItemLite` schema: `frequency: Optional[int] = None`, `duration_days: Optional[int] = None`, 기타 nullable 필드 검토 (`dose_amount`, `dose_unit` 등도 LLM이 null 자주 반환 시 Optional)
  - `RawOcrItemListLite` 컨테이너
  - `GeminiVisionLiteAdapter` 클래스: 기존 `GeminiVisionAdapter`와 동일 구조, schema/parser만 lite 사용
  - 기본 model = `gemini-2.5-flash-lite`
- ExtractedDrugItem (도메인) 변환 시: frequency null → default 1 (또는 0), duration_days null → default 7 (또는 0). 도메인 ExtractedDrugItem 자체는 Integer wrapper라 null 허용 검토

**참고**: `RawOcrItem` (flash) → `RawOcrItemLite` (lite) 둘 다 같은 도메인 모델로 변환. 도메인은 변경 X.

### 2. main.py factory

`back/ai_server/app/main.py`
```python
def _build_vision(settings):
    if 'lite' in settings.gemini_model:
        from app.rag.ocr.vision_lite import GeminiVisionLiteAdapter
        return GeminiVisionLiteAdapter(api_keys=settings.gemini_keys, model=settings.gemini_model)
    else:
        from app.rag.ocr.vision import GeminiVisionAdapter
        return GeminiVisionAdapter(api_keys=settings.gemini_keys, model=settings.gemini_model)
```

또는 `OCR_VISION_VARIANT` 환경변수 명시 분기 (model name보다 명확):
```python
variant = os.getenv('OCR_VISION_VARIANT', 'auto')  # auto/flash/lite
if variant == 'auto':
    variant = 'lite' if 'lite' in settings.gemini_model else 'flash'
```

CTO 권장: **model name 추론** (단순). 환경변수는 override 옵션.

### 3. correction.py도 동일 패턴 (선택)

`back/ai_server/app/rag/ocr/correction.py` 도 동일하게 lite 호환성 문제 있을 수 있음:
- 우선 vision만 fix → e2e로 correction 영향 확인 → 필요 시 별도 task

본 task 범위: vision만. correction은 호출 빈도 적고 LLM 출력 schema가 더 단순(string)이라 우선순위 낮음.

### 4. 테스트
- `back/ai_server/tests/test_vision_lite.py` (NEW) — RawOcrItemLite 파싱 케이스:
  - frequency=null 정상 파싱
  - duration_days=null 정상 파싱
  - 정상 정수 값도 파싱
  - 응답 형식 변형 (배열 wrap 등)
- 기존 `test_vision.py` 회귀 0 (flash schema 무변경)
- main.py factory 단위 (model name → adapter 선택)
- pytest 전체 회귀 0

### 5. 인수
1. `back/ai_server/app/rag/ocr/vision.py` (flash) **무변경** — 정확도 손실 0
2. `vision_lite.py` 신규 — Optional schema로 lite null 응답 정상 처리
3. main.py가 `gemini_model` 값으로 adapter 자동 선택
4. e2e: 시뮬레이터 갤러리 IMG_0001.JPG → OCR 200 + items 정상 응답 (504 사라짐)
5. pytest 회귀 0
6. 환경변수 `GEMINI_MODEL=gemini-2.5-flash` 로 변경 시 다시 flash adapter 사용 (즉시 갈아끼우기 가능)

### 6. 보고
`.cmux/messages/cto/inbox/T-OCR-VISION-VARIANT-be-done.json`
포함: 신규/변경 파일 + pytest + 시뮬레이터 IMG_0001 e2e 결과 + git status + adapter 선택 검증

## 비-범위

- correction.py lite 호환 (별도 task, 필요 시)
- prompt 자체 lite 최적화 (별도, 정확도 측정 후)
- 모델별 prompt 분리 (단일 prompt 유지 — 차이 검증 후 결정)
