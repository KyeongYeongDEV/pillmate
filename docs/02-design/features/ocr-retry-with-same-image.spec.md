# T-OCR-RETRY-SAME-IMAGE — 다시 시도 시 기존 이미지 재사용

작성일: 2026-06-28
사용자 명시: "다시 시도를 누르면 다시 이미지를 선택하거나 사진을 찍는 게 아니라 기존 이미지로 다시 시도는 못해?"

## 진단

현재 `handleRetry`:
```typescript
const handleRetry = useCallback(() => {
  setOcrError(false);
}, []);
```

→ ocrError state만 해제 → 사용자가 카메라/갤러리 화면으로 돌아감 → 새 사진 촬영/선택 필요. 비효율적.

## CTO 결정

**같은 image URI로 ocrExtract 재호출**. 이미 S3 업로드된 imageKey 재사용 → 업로드 skip → ocrExtract만 재호출.

장점:
- 사용자 UX: 한 탭으로 즉시 재시도
- 비용: S3 업로드 1회만 (재사용)
- AI dedupe: ai_server SHA-256 캐시 hit 시 즉시 응답 (in-flight dedupe + 캐시 합동 효과)
- in-flight dedupe(#11): 동시에 진행 중이면 결과 공유

## 절대 규칙

- BE 변경 X (FE only)
- git commit/push 금지 (CTO 단독)
- clean-code SRP: image state 보관 + processImage 재사용 가능 함수로
- no-overengineering: state 추가만, 신규 컴포넌트 X

---

## FE-Dev 작업

### 1. camera.tsx / scan.tsx 변경

#### 1.1 state 추가
```typescript
const [lastProcessed, setLastProcessed] = useState<{ uri: string; imageKey: string } | null>(null);
```

#### 1.2 processImage 분리
기존 `processImage(asset)` 을 두 단계로:
- `uploadImage(uri)`: issueUploadUrl + S3 PUT → imageKey 반환, lastProcessed state 저장
- `runOcrExtract(imageKey)`: ocrExtract 호출 + addFromExtract dispatch
- `processImage(uri)`: 새 이미지 — upload + extract 둘 다
- `retryOcr()`: lastProcessed.imageKey 있으면 extract만, 없으면 fallback

#### 1.3 handleRetry 변경
```typescript
const handleRetry = useCallback(async () => {
  if (lastProcessed) {
    setOcrError(false);
    setLoading(true);
    try {
      await runOcrExtract(lastProcessed.imageKey);
      router.replace('/prescription/review');
    } catch {
      setOcrError(true);
    } finally {
      setLoading(false);
    }
  } else {
    setOcrError(false);
  }
}, [lastProcessed, runOcrExtract]);
```

#### 1.4 in-flight dedupe 호환
- useOcrInFlight hook 의 begin/end 도 retry 시 호출 (같은 image hash → join future 또는 캐시 hit)
- hashImageUri(lastProcessed.uri) 그대로 사용 → in-flight registry 자동 dedupe

### 2. UX 추가 (선택)
- OcrProgress phase='failed' 버튼은 그대로 "다시 시도" / "뒤로"
- "다시 시도" = 기존 이미지 재사용 (사용자 의도)
- "뒤로" = 카메라/갤러리 화면 복귀 (새 이미지 선택 가능)
- 추가 옵션 "다른 사진" 버튼은 본 task에서는 보류 (UX 복잡도 ↑, 사용자 명시 X)

### 3. 테스트
- `camera.tsx`/`scan.tsx` 단위/통합 — retry 시나리오 검증 (lastProcessed 있을 때 upload 호출 0, extract만 호출)
- 기존 cameraFlow.test.tsx 갱신 또는 신규
- jest + tsc 0

### 4. 인수
1. 에러 후 "다시 시도" → issueUploadUrl + S3 PUT 호출 0 (네트워크 탭 또는 mock 검증)
2. ocrExtract만 재호출 (같은 imageKey)
3. 성공 시 review.tsx로 정상 이동
4. "뒤로" → 카메라/갤러리 화면 복귀 (새 이미지 선택 가능)
5. in-flight dedupe + 캐시 hit 시 즉시 응답 가능
6. jest + tsc 0

### 5. 보고
`.cmux/messages/cto/inbox/T-OCR-RETRY-SAME-IMAGE-fe-done.json`
포함: 변경 파일 + 단위/통합 결과 + git status

## 비-범위

- "다른 사진" 버튼 추가 (사용자 명시 X, UX 복잡도)
- BE에서 retry 자동화 (사용자 의도와 어긋남)
- imageKey TTL/만료 처리 (S3 presigned URL는 유효기간 있음 — Phase 2 검토)
