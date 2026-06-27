# T-OCR-EFFICIENCY — OCR 호출 효율화 (in-flight dedupe + FE 재시도 차단 + 이미지 다운사이징)

작성일: 2026-06-28
사용자 명시:
- "다시 시도가 뜨면 만약에 OCR 인식 중인데 뜨는 걸 수도 있잖아. 그럼 쓸데없이 요청만 한 건데. 이걸 좀 더 효율적으로 해결하는 방법"

## 진단

현재 낭비 패턴:
```
사용자 → BE → ai_server LLM (47s 진행 중)
60s timeout → BE 에러 → FE "다시 시도" 화면 (ai_server는 계속 처리, 결과 캐시 저장)
사용자 "다시 시도" → 새 요청 → ai_server SHA-256 cache miss (아직 저장 안 됨) → 또 LLM 호출 → quota 소진
```

핵심: ai_server 측 SHA-256 캐시는 **응답 받은 후만 저장**이라 진행 중 dedupe 안 됨. + FE는 같은 이미지 중복 요청 차단 없음. + 이미지 크기 그대로 전송이라 LLM 처리 시간 김.

## 절대 규칙 (재확인)

- BE/FE 모두 git commit/push 금지 (CTO 단독)
- DB 무관 — DELETE/TRUNCATE/DROP 0
- 의료 안전 graceful 유지 (캐시 hit 도 source/confidence 보존)
- no-overengineering: asyncio standard + ConcurrentHashMap 표준 + expo-image-manipulator(이미 expo 환경)
- TDD: in-flight dedupe 동시 요청 시나리오 테스트 필수

---

## A. BE-Dev (ai_server) — in-flight dedupe

### A.1 영향 파일
- `back/ai_server/app/rag/ocr/service.py` (OcrPrescriptionService)
- `back/ai_server/app/rag/ocr/cache.py` (기존 SHA-256 캐시 옆에 InFlightRegistry 추가)
- `back/ai_server/tests/test_ocr_in_flight.py` (NEW)

### A.2 구현 패턴 (asyncio)
```python
class InFlightRegistry:
    def __init__(self):
        self._lock = asyncio.Lock()
        self._futures: dict[str, asyncio.Future] = {}
    
    async def get_or_create(self, key: str) -> tuple[asyncio.Future, bool]:
        """returns (future, is_owner). is_owner=True 면 호출자가 실제 작업 수행."""
        async with self._lock:
            if key in self._futures:
                return self._futures[key], False
            future = asyncio.get_running_loop().create_future()
            self._futures[key] = future
            return future, True
    
    async def complete(self, key: str, result):
        async with self._lock:
            future = self._futures.pop(key, None)
        if future and not future.done():
            future.set_result(result)
    
    async def fail(self, key: str, exc):
        async with self._lock:
            future = self._futures.pop(key, None)
        if future and not future.done():
            future.set_exception(exc)
```

### A.3 OcrPrescriptionService.process 통합
```python
async def process(self, request):
    image_bytes = await self._fetch(request)
    hash_hex = image_hash(image_bytes)
    
    # 1. cache hit
    cached = await self._cache.get(hash_hex)
    if cached:
        return cached
    
    # 2. in-flight dedupe — 같은 hash 진행 중이면 join
    future, is_owner = await self._in_flight.get_or_create(hash_hex)
    if not is_owner:
        return await future  # 다른 요청이 처리 중, 결과 공유
    
    # 3. 새 처리
    try:
        result = await self._process_inner(image_bytes, hash_hex, request)
        await self._cache.set(hash_hex, result)
        await self._in_flight.complete(hash_hex, result)
        return result
    except Exception as exc:
        await self._in_flight.fail(hash_hex, exc)
        raise
```

### A.4 테스트 (pytest)
- `test_in_flight_dedupe_same_hash_single_llm_call`: 동시 5 요청 같은 hash → LLM 호출 1번 검증 (Stub LLM call count)
- `test_in_flight_release_after_success`: 응답 후 in-flight map 비어 있는지
- `test_in_flight_release_after_exception`: 실패도 cleanup 되는지
- `test_cache_hit_skips_in_flight`: 캐시 hit 시 in-flight 생성 안 됨
- `test_owner_failure_propagates_to_joiners`: owner 실패 시 join한 요청도 에러 받음

### A.5 인수
1. 동시 5 요청 같은 image_hash → ai_server LLM 호출 정확히 1번
2. timeout 후 재시도 (캐시 hit) → 즉시 응답 < 1초
3. ai_server 다운 시 in-flight 자동 cleanup, FE 정상 에러 받음
4. 다른 image_hash 요청은 병렬 처리 영향 없음

### A.6 보고
`.cmux/messages/cto/inbox/T-OCR-EFFICIENCY-be-done.json`
포함: 변경 파일 + pytest 결과 + e2e 테스트(curl로 동시 호출) + LLM 호출 카운트

---

## C. FE-Dev — 재시도 차단 + 진행 상황 표시

### C.1 영향 파일
- `front/src/app/prescription/camera.tsx`
- `front/src/app/prescription/scan.tsx`
- `front/src/hooks/useOcrInFlight.ts` (NEW — SRP hook)
- `front/tests/unit/useOcrInFlight.test.ts` (NEW)

### C.2 구현
- 이미지 선택/촬영 → `expo-crypto` 로 SHA-256 hash 계산
- AsyncStorage `ocr-in-flight:{hash}` = `{startTime, attempts}` (TTL 5분)
- 같은 hash 호출 시:
  - 첫 호출이 진행 중이면 → 진행 표시 ("이미 인식 중입니다. {경과}s 경과") + button disable
  - 5분 초과 또는 응답 도착 시 cleanup
- 성공/실패 결과 도착 → 즉시 cleanup
- "다시 시도" 버튼 클릭 → 동일 hash 라면 새 요청 X, 진행 토스트만

### C.3 UX 디테일
- 진행 표시 1: "✨ AI 분석 중… {경과}s" (button disable, 5s 단위 갱신)
- 진행 표시 2: 30s 초과 시 "오래 걸리네요. 잠시만 더..." 메시지 변경
- 60s 초과 시 "다시 시도" 활성화 (그래도 동일 hash면 cancel & 재호출)
- 다른 이미지 선택은 언제든 가능

### C.4 테스트
- `useOcrInFlight` hook 단위 테스트 (시작/중복 차단/cleanup/타임아웃)
- camera/scan 통합 분기 테스트 (성공/실패/중복)

---

## D. FE-Dev — 이미지 다운사이징

### D.1 영향 파일
- `front/src/lib/imageProcessing.ts` (NEW)
- `front/src/app/prescription/camera.tsx` (촬영 결과 처리)
- `front/src/app/prescription/scan.tsx` (갤러리 결과 처리)
- 또는 S3 업로드 직전 공통 hook
- `front/tests/unit/imageProcessing.test.ts` (NEW)

### D.2 구현
- `expo-image-manipulator` 사용 (이미 expo 환경, 추가 native 의존 X)
- `manipulateAsync(uri, [{ resize: { width: 1024 } }], { compress: 0.8, format: 'jpeg' })`
- 처방전 텍스트 인식에 1024px 충분 (Gemini 입력 토큰 ~ 픽셀 수 비례)
- 결과 크기 비교 로그 (개발 시 측정)

### D.3 인수
- 원본 4MB 이미지 → 약 200-500KB
- Gemini 처리 시간 50% 단축 (체감)
- OCR 정확도 변화 ≤ 5% (이미 1024px도 충분)

### D.4 테스트
- `imageProcessing.test.ts` — resize 로직 검증 (mock manipulateAsync)
- 통합: 큰 이미지(4MB) → 작은 이미지 후 OCR 정상 작동 (수동 검증)

---

## 비-범위 (out of scope)

- WebSocket/SSE 진행 상황 실시간 push — Phase 2~3 검토
- OCR job 비동기 + polling 패턴 (큰 구조 변경) — 별도 spec
- Redis 분산 in-flight (멀티 인스턴스) — Phase 3 MSA 도입 시
- 다른 OCR provider 검토 — 별도 cost 평가

## 보고 종합

| Task | 파일 |
|------|------|
| BE | `T-OCR-EFFICIENCY-be-done.json` |
| FE | `T-OCR-EFFICIENCY-fe-done.json` |
