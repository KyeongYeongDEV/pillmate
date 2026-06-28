# T-OCR-CASCADE — Model Cascade Vision Adapter (lite 1차 + flash fallback)

작성일: 2026-06-28
사용자 명시: "lite로 OCR/인사이트 다 진행하고 retry 다 실패하면 flash로 재시도. 비슷한 기법 토대로 추천."
권장 패턴: Model Cascade (PillMate cost-aware 룰 부합)

## 진단

- flash-lite 단독: 약봉투 OCR 정확도 매우 낮음 (12장 0/12 매칭, 504+"???" 다발)
- flash 단독: 무료 quota 20 RPD 제한
- 절충: **lite 1차 → 실패/저품질 시 flash fallback** (cascade)

## 결정

신규 `CascadeVisionAdapter` (Decorator pattern):
- primary = `GeminiVisionLiteAdapter` (빠름, 무료 quota ↑)
- fallback = `GeminiVisionAdapter` (정확, quota ↓)
- 1차 실패 조건: exception 또는 quality heuristic (sentinel detect)

## 절대 규칙

- TDD (RED → GREEN → REFACTOR)
- DDD 의존 역전 X (application/infrastructure)
- clean-code SRP (routing/품질판단 분리)
- no-overengineering: LangChain RunnableWithFallbacks 대신 수동 Decorator
- medical/graceful: cascade 둘 다 실패 시 빈 결과 (등록 정상 유지)
- cost-aware: lite quota 우선 활용, flash는 escalate 시만
- in-flight dedupe / SHA-256 캐시와 호환 (key 무변경)
- git commit/push 금지 (CTO 단독)
- BE only (ai_server)

---

## BE-Dev 작업

### 1. 신규 파일 `back/ai_server/app/rag/ocr/cascade_vision.py`

```python
import asyncio
import logging
from app.rag.ocr.vision import GeminiVisionAdapter, VisionInvocationError
from app.rag.ocr.vision_lite import GeminiVisionLiteAdapter
from app.rag.ocr.schemas import RawOcrItem  # 도메인 raw type

logger = logging.getLogger(__name__)

LOW_CONFIDENCE_THRESHOLD = 0.3      # 모든 item confidence < 이 값이면 fallback
UNKNOWN_NAME_SENTINEL = "???"        # name_raw 가 모두 sentinel 이면 fallback


class CascadeVisionAdapter:
    """lite 1차 → 실패/저품질 시 flash fallback (Model Cascade)."""

    def __init__(self, primary: GeminiVisionLiteAdapter, fallback: GeminiVisionAdapter):
        self._primary = primary
        self._fallback = fallback

    async def extract(self, image_bytes: bytes) -> list[RawOcrItem]:
        try:
            result = await self._primary.extract(image_bytes)
        except (VisionInvocationError, asyncio.TimeoutError) as exc:
            logger.warning("cascade primary failed type=%s → flash fallback", type(exc).__name__)
            return await self._fallback.extract(image_bytes)

        if self._is_low_quality(result):
            logger.info("cascade primary low_quality → flash fallback (items=%d)", len(result))
            return await self._fallback.extract(image_bytes)
        return result

    def _is_low_quality(self, items: list[RawOcrItem]) -> bool:
        if not items:
            return True
        if all(_unknown_name(it) for it in items):
            return True
        if all(_low_confidence(it) for it in items):
            return True
        return False


def _unknown_name(item) -> bool:
    name = getattr(item, "name_raw", None) or ""
    return name.strip() == UNKNOWN_NAME_SENTINEL


def _low_confidence(item) -> bool:
    conf = getattr(item, "confidence", None)
    return conf is None or float(conf) < LOW_CONFIDENCE_THRESHOLD
```

### 2. main.py factory 분기

```python
def _resolve_vision_variant(settings) -> str:
    override = settings.ocr_vision_variant
    if override and override != "auto":
        return override  # flash | lite | cascade
    return "lite" if "lite" in settings.gemini_model else "flash"


def _build_vision(settings):
    variant = _resolve_vision_variant(settings)
    if variant == "cascade":
        from app.rag.ocr.cascade_vision import CascadeVisionAdapter
        from app.rag.ocr.vision import GeminiVisionAdapter
        from app.rag.ocr.vision_lite import GeminiVisionLiteAdapter
        primary = GeminiVisionLiteAdapter(api_keys=settings.gemini_keys, model="gemini-2.5-flash-lite")
        fallback = GeminiVisionAdapter(api_keys=settings.gemini_keys, model="gemini-2.5-flash")
        logger.info("OCR vision adapter=cascade primary=flash-lite fallback=flash")
        return CascadeVisionAdapter(primary, fallback)
    # 기존 flash/lite 분기 유지
    ...
```

### 3. config.py
- `ocr_vision_variant` 이미 추가됨 (T-OCR-VISION-VARIANT) — 값 `'cascade'` 허용 명시 (validator 갱신)

### 4. 환경변수
- `OCR_VISION_VARIANT=cascade` 으로 활성 (docker-compose.override.yml 또는 .env)
- 기존 `flash` / `lite` / `auto` 도 유지

### 5. 테스트
- `back/ai_server/tests/test_cascade_vision.py` (NEW)
  - `test_cascade_primary_success_returns_primary` — primary 정상 → fallback 미호출
  - `test_cascade_primary_exception_fallback` — primary throws → fallback 호출 → fallback 결과 반환
  - `test_cascade_primary_low_quality_fallback` — primary all "???" → fallback 호출
  - `test_cascade_primary_low_confidence_fallback` — primary all confidence < 0.3 → fallback
  - `test_cascade_both_fail_propagates` — primary throws + fallback throws → 외부에 raise
  - `test_factory_cascade_variant` — `OCR_VISION_VARIANT=cascade` → CascadeVisionAdapter 인스턴스 반환
- 기존 vision/lite 테스트 회귀 0

### 6. 인수
1. `cascade_vision.py` 신규, primary/fallback 분리
2. factory `OCR_VISION_VARIANT=cascade` 시 cascade adapter 선택 (기동 로그 "adapter=cascade")
3. 갤러리 IMG_0001~0013 12장 cascade로 일괄 e2e:
   - lite 성공한 케이스: lite 결과 그대로 (flash 호출 0)
   - lite 실패한 케이스: flash fallback 호출 + 정상 items 반환
   - 매칭률 lite 단독(0/12) 대비 ↑
4. pytest 회귀 0
5. in-flight dedupe / SHA-256 캐시 호환 (cascade 호출도 같은 image_hash 키)

### 7. 보고
`.cmux/messages/cto/inbox/T-OCR-CASCADE-be-done.json`
포함: 변경 파일 + pytest + 갤러리 12장 e2e 결과(lite-only vs cascade 매칭률 비교) + git status + adapter 활성 로그

## 비-범위

- correction.py cascade — 별도 task (필요 시)
- LangChain RunnableWithFallbacks 도입 — 별도 검토 (현 구조 충분)
- Circuit Breaker (Resilience4j 패턴) — Phase 2+
- 메트릭 노출 (Prometheus fallback_count) — 별도 task
