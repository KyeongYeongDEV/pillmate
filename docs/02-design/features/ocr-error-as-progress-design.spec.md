# T-OCR-ERROR-DESIGN — OCR 에러 화면을 OcrProgress 디자인으로 통합

작성일: 2026-06-28
사용자 명시:
1. "이 디자인 너무 좋아 무조건 이런 느낌 유지" (OcrProgress: AI 분석 중 + 단계 + progress bar)
2. "30초쯤 지나니까 에러 화면 떠" (현재 ocrError 화면은 다른 디자인)

## 진단

현재 구조:
```
camera.tsx / scan.tsx:
  if (loading)  → <OcrProgress />                    [✨ 사용자 좋아하는 디자인]
  if (ocrError) → <View>약 인식에 시간 오래…</View>    [다른 디자인, 사용자 거부감]
```

BE quota 429 → ai_server retry 5-30s → BE 에러 응답 → FE setOcrError(true) → **OcrProgress unmount + 에러 화면 표시** (30초쯤 발생).

사용자 의도: **에러 화면도 OcrProgress와 같은 디자인 lang** 유지. 단계/progress 영역 유지하되 메시지/버튼만 에러 상태로.

## CTO 결정

**OcrProgress 컴포넌트에 `phase` prop 추가**: `progressing` | `failed`
- `progressing` (default): 현재 OcrProgress 동작 (시간 베이스 progress + 단계)
- `failed`: 같은 layout, 단계 표시 유지(마지막 단계 ❌ 표시 + 빨간 강조), 메시지 "약 인식 실패" + "다시 시도" / "뒤로" 두 버튼

## 절대 규칙

- BE 변경 X (FE only)
- git commit/push 금지 (CTO 단독)
- clean-code SRP: phase 분기는 OcrProgress 내부 명확 분리 (별 컴포넌트 ErrorOcrProgress 생성 X — 한 컴포넌트가 두 상태 표현 = 디자인 일관성)
- 매직넘버 X: ICON_DONE/ACTIVE/PENDING/FAILED 상수
- 디자인 토큰: 빨간 강조는 `colors.statusNegative` 또는 동등

---

## FE-Dev 작업

### 1. OcrProgress 컴포넌트 확장
파일: `front/src/components/prescription/OcrProgress.tsx`
```typescript
interface Props {
  onRetry: () => void;
  onBack?: () => void;
  phase?: 'progressing' | 'failed';  // NEW, default 'progressing'
}
```

#### 1.1 progressing (현재 그대로)
- 헤더: "AI가 약을 분석하고 있어요"
- 부제: "{초} 경과"
- progress bar (시간 베이스)
- 단계 [✓ 이미지 업로드] [⟳ AI 약 인식 중...] [○ 약 정보 매칭]
- 60s 후 "다시 시도" 버튼만 노출 (기존)

#### 1.2 failed (신규)
- 같은 외곽/레이아웃 유지
- 헤더: "약 인식에 실패했어요" (빨간 강조 또는 굵게)
- 부제: "잠시 후 다시 시도해 주세요"
- progress bar: 단색 빨간/회색 100% 채움 또는 마지막 단계 cap (디자인 결정)
- 단계 표시 유지: [✓ 이미지 업로드] [✗ AI 약 인식 실패] [— 약 정보 매칭]
  - ✗는 빨간 X icon (colors.statusNegative)
- 버튼: "다시 시도" (primary) + "뒤로" (secondary) — 가로 배열

### 2. camera.tsx / scan.tsx 통합
- 기존 `if (ocrError) { return <View>...</View> }` 블록 삭제
- 대신 `<OcrProgress phase={ocrError ? 'failed' : 'progressing'} onRetry={...} onBack={...} />` 단일 분기
- 또는 if (loading || ocrError) → <OcrProgress phase={...} />

### 3. 스타일/토큰
- statusNegative 또는 colors.red 계열로 ✗ + 헤더 강조
- 다크모드 자동
- 기존 progressing 디자인은 변동 X

### 4. 테스트
- `front/tests/unit/OcrProgress.test.tsx` 갱신:
  - 기존 9 케이스 유지 (progressing 기본)
  - 신규 추가: phase='failed' 시 헤더 "실패" / ✗ icon / 두 버튼 검증

### 5. 인수
1. progressing 화면 시각 무변경 (사용자가 좋아하는 디자인)
2. failed 시 같은 layout + ✗ + "다시 시도" + "뒤로" 버튼
3. ocrError → setOcrError(true) → OcrProgress phase='failed' 표시 (unmount X)
4. jest + tsc 0
5. 회귀 0

### 6. 보고
`.cmux/messages/cto/inbox/T-OCR-ERROR-DESIGN-fe-done.json`
포함: 변경 파일 + jest/tsc + 디자인 설명 + git status

## 비-범위

- 에러 종류 세분화 (timeout/quota/network) — Phase 2 검토
- 에러 발생 시 자동 retry 정책 — Phase 2
- progress bar 색상 다른 옵션 — 본 task 후 사용자 피드백 받고 조정
