# Feature Loop — 멀티에이전트 반복 루프

> CTO ↔ Developer ↔ QA-Sonnet ∥ QA-Gemini 4-에이전트의 한 task 처리 흐름

---

## 한 눈에

```
┌─ 사용자 ─┐
│ 요청      │
└──┬───────┘
   ▼
┌──────────────────────────────────────────────────────────────┐
│ CTO: docs/agents/tasks/{ID}-{slug}.md 발행                    │
└──┬───────────────────────────────────────────────────────────┘
   ▼
┌──────────────────────────────────────────────────────────────┐
│ Developer: 구현 (TDD) → docs/agents/developer/{ID}-iter-N.md │
└──┬───────────────────────────────────────────────────────────┘
   │           ┌──────────────────────────────────────┐
   ├──────────►│ QA-Sonnet → qa-sonnet/{ID}-iter-N.md │
   │           └──────────────────────────────────────┘
   │           ┌──────────────────────────────────────┐
   └──────────►│ QA-Gemini → qa-gemini/{ID}-iter-N.md │
               └──────────────────────────────────────┘
   ▼
┌──────────────────────────────────────────────────────────────┐
│ CTO: 두 리포트 교차 비교 → decisions/{ID}-iter-N.md           │
│  - 두 QA 모두 PASS → 사용자 보고 + 커밋·푸시 승인 요청        │
│  - 하나라도 FAIL → tasks/{ID}-iter-{N+1}.md 발행 (자동 재진입) │
│  - 5회 초과 → ESCALATE: 사용자 결정 요청                       │
└──────────────────────────────────────────────────────────────┘
```

---

## 상세 단계

### 0. 사전 준비 (한 번만)
- `./.cmux/scripts/setup-worktrees.sh` 실행
- `GEMINI_API_KEY` 환경변수 설정
- CMUX 4-패널 띄우고 각 에이전트 모델·디렉토리 지정

### 1. 사용자 → CTO
- 사용자가 CTO 패널에 자연어로 요청
- CTO는 모호하면 `AskUserQuestion`으로 재질문
- 합의된 요구를 task 명세로 변환

### 2. CTO → Task 발행
- 파일: `docs/agents/tasks/{ID}-{slug}.md`
- 다음 iter는: `docs/agents/tasks/{ID}-iter-{N+1}.md` (slug 없이)
- 이전 iter QA 리포트의 Critical 항목을 task body에 명시적으로 인용

### 3. Developer → 구현
- 자기 worktree (`worktrees/developer`) 에서 작업
- 브랜치: `feat/cmux-task-{ID}` (이미 있으면 그 위에서 추가 작업)
- TDD 사이클 강제, 각 사이클마다 커밋 (로컬)
- 완료 후 `docs/agents/developer/{ID}-iter-N.md` 작성

### 4. QA 패널 (병렬)

#### 4.1 QA-Sonnet
- Developer 브랜치를 `git fetch` 후 read-only 검토
- `.cmux/agents/qa-sonnet.md` 체크리스트 수행
- `docs/agents/qa-sonnet/{ID}-iter-N.md` 작성

#### 4.2 QA-Gemini
- Developer 브랜치 fetch
- 변경 파일 목록과 diff를 prompt로 구성:
  ```bash
  {
    echo "## Task"
    cat docs/agents/tasks/{ID}*.md
    echo "## Developer 보고"
    cat docs/agents/developer/{ID}-iter-N.md
    echo "## Diff"
    git diff main...work/developer -- '*.java' '*.py' '*.sql'
  } | ./.cmux/scripts/qa-gemini.sh > docs/agents/qa-gemini/{ID}-iter-N.md
  ```

> **두 QA는 서로의 리포트를 읽지 않는다.**
> 같은 task를 독립적으로 검증해 false-PASS 위험을 줄인다.

### 5. CTO → 판정
- 두 리포트가 모두 도착할 때까지 대기
- `docs/agents/decisions/{ID}-iter-N.md` 작성
- verdict:
  - `PASS` — 두 QA 모두 PASS + CTO 추가 검토 통과
  - `RETRY` — 적어도 한 쪽이 FAIL
  - `ESCALATE` — 두 QA가 서로 다른 결과 + CTO도 판단 곤란 → 사용자

### 6. 분기

#### 6.1 PASS
- 사용자에게 한 줄 요약 보고 + 커밋·푸시 승인 요청
- 승인 시:
  - Developer worktree의 작업 브랜치를 push
  - 필요 시 main으로 머지 (사용자 결정)
  - 다음 task 또는 종료

#### 6.2 RETRY
- 자동으로 N+1 iter task 발행
- 발행 task에는 Critical 항목만 인용 (Major/Minor는 별도 라벨)
- iter 카운터 ++

#### 6.3 ESCALATE
- 사용자에게 두 QA 리포트의 핵심 차이 + CTO 분석 + 권고안 제시
- 사용자 결정 후 재진입

---

## 종료 조건

- **PASS** → 사용자 승인 후 푸시·종료
- **iter ≥ 5** → 강제 ESCALATE (무한 루프 방지)
- 사용자가 `중단` 명령 → 즉시 정지, 현재 상태 요약

---

## 안티 패턴

- QA가 Developer 보고서만 보고 코드 안 읽기 → 직접 diff 검토 필수
- CTO가 한 QA만 보고 판정 → 두 리포트 도착 후만 판정
- Developer가 "다음 iter에서..." 미루기 → task 범위 밖이면 별도 task로 분리
- 두 QA가 서로 리포트를 참조 → 독립성 깨짐 (CTO만 종합)

## 참조

- `.cmux/agents/cto.md`
- `.cmux/agents/developer.md`
- `.cmux/agents/qa-sonnet.md`
- `.cmux/agents/qa-gemini.md`
- `.claude/skills/commit-convention.md`
