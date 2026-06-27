# 멀티에이전트 하네스 — 신규 프로젝트 재현 가이드

> PillMate 의 현재 하네스 (cmux 7 패널 + 10단계 PDCA + 자동 메모리) 를 신규 프로젝트에서 그대로 복원하는 단계.
> **마지막 검증**: 2026-06-01 (PillMate 60+ commits 운영 후 안정 검증)

---

## 0. 사전 요구사항 (한 번만)

```bash
# macOS
brew install --cask cmux        # cmux 데스크탑 앱
npm install -g @anthropic-ai/claude-cli   # claude CLI
npm install -g @google/gemini-cli         # gemini CLI (QA-Gemini 용)
brew install jq                # (옵션) JSON 처리
```

확인:
```bash
cmux version && claude --version && gemini --version && node --version
```

---

## 1. 신규 프로젝트 디렉토리 + cmux workspace 생성

```bash
mkdir -p ~/Downloads/myproject && cd ~/Downloads/myproject
git init && git checkout -b dev
cmux .                          # 현재 디렉토리를 cmux workspace 로 열기
```

cmux UI 에서 workspace 생성됨. 다음 step 의 셋업 스크립트가 7 패널 자동 생성.

---

## 2. 하네스 셋업 스크립트 1회 실행

신규 프로젝트 루트에서 본 PillMate 의 핵심 파일 복사 + 셋업:

```bash
PILLMATE=/Users/user/Downloads/pillmate
PROJECT=$(pwd)

# 핵심 디렉토리 구조 복사
cp -r $PILLMATE/.cmux $PROJECT/             # 헬퍼 스크립트 + 프롬프트 + 셋업
cp -r $PILLMATE/.claude $PROJECT/           # 룰 + agents + contexts + skills
mkdir -p $PROJECT/docs/{01-plan,02-design,architecture,harness-evolution}

# 프로젝트별 변경이 필요한 것 (다음 step 에서 직접 수정)
# - CLAUDE.md (프로젝트 instructions)
# - .cmux/prompts/*.md (BE/FE 스택 변경 시)
# - .claude/rules/{java,python,sql} (도메인 룰)
```

---

## 3. cmux 7 패널 자동 생성

`cmux current-workspace` 로 workspace ID 확인 후 셋업 실행:

```bash
cd $PROJECT
./.cmux/setup-team-be-fe.sh    # 5 패널 (CTO + BE + FE + QA-Claude + QA-Gemini) 자동 생성
```

생성 후 **2 패널 (Reviewer + Researcher) 추가** — CTO 가 첫 디스패치 시 자동 생성하거나 수동:

```bash
WORKSPACE=$(cmux current-workspace | tr -d ' ')

# Reviewer (surface:44)
cmux new-pane --workspace $WORKSPACE --type terminal --direction down --focus false
cmux rename-tab --workspace $WORKSPACE --surface <new-surface> "Reviewer"
# 새 surface 에 system prompt 부착:
cmux send-panel --panel <new-surface> --workspace $WORKSPACE -- "cd $PROJECT && claude --dangerously-skip-permissions --append-system-prompt-file '.cmux/prompts/reviewer.md'"

# Researcher (surface:45) — 같은 패턴
```

→ `.cmux/.runtime/cmux.env` 에 6 surface ID 등록:
```bash
CMUX_WORKSPACE='<workspace-id>'
CTO_SURFACE='surface:N1'
BE_DEV_SURFACE='surface:N2'
FE_DEV_SURFACE='surface:N3'
QA_CLAUDE_SURFACE='surface:N4'
QA_GEMINI_SURFACE='surface:N5'
REVIEWER_SURFACE='surface:N6'
RESEARCHER_SURFACE='surface:N7'
```

---

## 4. 7 패널 역할 + 권한

| Surface | Panel | CLI | 모델 | 권한 |
|---|---|---|---|---|
| CTO | claude | Opus | spec / rules / memory / git push / docker ops |
| BE-Dev | claude | Sonnet | back/** 수정 |
| FE-Dev | claude | Sonnet | front/** 수정 |
| QA-Claude | claude | Sonnet | READ-ONLY 실행 검증 (./gradlew test, npm test, curl) |
| QA-Gemini | gemini | Gemini 2.5 Pro | READ-ONLY 정밀 (docker, curl, DB SELECT) |
| Reviewer | claude | Sonnet | READ-ONLY 코드 정적 품질 (9 체크리스트) |
| Researcher | claude | Sonnet | READ-ONLY 코드 조사 (4섹션 markdown) |

→ **CTO 는 코드 수정 절대 X** (1줄·핫픽스도 BE/FE 디스패치 의무). 룰: `.claude/rules/common/cto-no-code-edit` 또는 `.cmux/prompts/cto.md` 의 "🚨 코드 수정 금지" 섹션.

---

## 5. CTO 10단계 PDCA 흐름

`.cmux/prompts/cto.md` 의 "한 task 의 흐름" 참고:

```
1. 사용자 요구 파악 (모호하면 AskUserQuestion)
2. Researcher 디스패치 (say researcher) — 4섹션 markdown (영향 파일/컨벤션/깨질 caller/선례)
3. spec 작성 (.cmux/specs/T-XXX.md — Researcher 결과 + DoD + 순서 + Risk + 의존성 + 룰 + 변경 파일 + 테스트 힌트)
4. 🚨 Plan 승인 게이트 — AskUserQuestion ("그대로 / 수정 / 중단")
5. Dev 디스패치 (say be / say fe)
6. DONE 시그널 수신 → 사용자 push 승인
7. Reviewer 디스패치 (say reviewer) — push 직후
8. QA-Claude + QA-Gemini 디스패치 (Option A, 양쪽 의무)
9. 리포트 종합
9.5 compact-all 6 패널 (./.cmux/bin/compact-all)
9.6 conversation-log 갱신 (정책 변경 시)
9.7 TaskUpdate completed
10. consensus FAIL → follow-up spec (2단계 부터)
```

---

## 6. 헬퍼 스크립트

`.cmux/bin/` 에 있음:

| 스크립트 | 역할 |
|---|---|
| `say <role> "msg"` | 패널에 메시지 + Enter 전송. DB-safety prefix 자동 부착. role: be / fe / qa-claude / qa-gemini / reviewer / researcher |
| `compact-all` | 모든 6 패널 `/compact` (Claude) 또는 `/compress` (Gemini) 자동 분기 |
| `clear-all` | 모든 6 패널 `/clear` 일괄 — **fallback** (compact 안 풀릴 때). 모든 패널 idle 시에만 사용 (진행 중 task 컨텍스트 손실 위험) |
| `dispatch <task_id> <spec>` | Dev 디스패치 단축 |
| `await-report <task_id>` | QA 통합 리포트 대기 |
| `status` | inbox/outbox 큐 상태 |

예시 사용:
```bash
./.cmux/bin/say be "[T-FOO — spec: .cmux/specs/T-FOO.md ...]"
./.cmux/bin/compact-all
```

---

## 7. 메모리 + 룰 + agents 구조

### 7.1 메모리 (자동 로드)
- 위치: `~/.claude/projects/<workspace-encoded>/memory/MEMORY.md` (인덱스) + 개별 `*.md` 파일
- Claude Code SessionStart hook 가 매 신규 세션 시 자동 prompt 주입
- 신규 패널 (Reviewer/Researcher) 도 user-scope 라 같은 메모리 자동 로드

### 7.2 룰
- `.claude/rules/common/` — language-agnostic (tdd-cycle / no-overengineering / clean-code / db-safety / medical-safety / cost-aware)
- `.claude/rules/java/` — Spring Boot 룰
- `.claude/rules/python/` — FastAPI 룰
- `.claude/rules/sql/postgres.md` — DB 룰

### 7.3 Agents (sub-agent 호출용)
- `.claude/agents/researcher.md` — Researcher prompt
- `.claude/agents/*.md` — 도메인별 sub-agent

### 7.4 contexts
- `.claude/contexts/ubiquitous-language.md` — Bounded Context 용어
- `.claude/contexts/evolution-story.md` — 진화 narrative

---

## 8. CLAUDE.md (프로젝트 instructions)

신규 프로젝트의 `CLAUDE.md` 에 다음 포함:

```markdown
# {프로젝트명} — Claude Code 하네스

## 🚨 절대 규칙
1. TDD: RED → GREEN → REFACTOR
2. DDD 레이어: presentation → application → domain ← infrastructure
3. {도메인 안전 룰 — 의료/금융/PII 등}
4. 오버엔지니어링 금지
5. **DB 데이터 삭제 절대 금지** (DELETE/TRUNCATE/DROP/UPDATE-no-WHERE)
6. **CTO 코드 직접 수정 금지** (1줄·핫픽스도 BE/FE 디스패치)

상세: `.claude/rules/`

## Bounded Contexts
| Context | Aggregate Root | 책임 |
| ... | ... | ... |

## 기술 스택
| ... | ... |

## 자주 쓰는 명령
| ... | ... |

## 에이전트 사용 가이드
| ... | ... |
```

---

## 9. 검증 (셋업 후 1회)

```bash
# 7 패널 모두 부팅 + system prompt 정확히 로드됐는지 ping
for role in be fe qa-claude qa-gemini reviewer researcher; do
  ./.cmux/bin/say $role "준비 확인 — 본인 역할 한 줄 + 권한 한 줄 응답"
done

# 5~10초 후 각 패널 응답 확인
for s in $BE_DEV_SURFACE $FE_DEV_SURFACE $QA_CLAUDE_SURFACE $QA_GEMINI_SURFACE $REVIEWER_SURFACE $RESEARCHER_SURFACE; do
  echo "=== $s ==="
  cmux read-screen --workspace $CMUX_WORKSPACE --surface $s --lines 10
done
```

---

## 10. 새 task 시작 (검증된 정상 흐름)

CTO 패널 (= 사용자가 직접 채팅) 에서 사용자가:

```
> 새 기능: {요구}
```

CTO 가 자동으로 10단계 흐름 실행:
```
1. AskUserQuestion 모호점
2. say researcher "[T-XYZ 조사 — 4섹션]"
3. .cmux/specs/T-XYZ.md 작성
4. AskUserQuestion Plan 승인
5. say be / say fe
6. DONE 시그널 → 사용자 push 승인
7. say reviewer
8. say qa-claude + say qa-gemini
9. 리포트 종합
9.5 compact-all
9.6 conversation-log 갱신
9.7 TaskUpdate completed
10. follow-up 또는 다음 task
```

---

## 11. cmux node-options hook 손상 시 (자주 발생)

증상: 모든 패널 spinner 만 돌고 멈춤 + `MODULE_NOT_FOUND requireStack: ['internal/preload']`

원인: `NODE_OPTIONS=--require=/var/folders/.../T/cmux-claude-node-options/restore-node-options.cjs` 의 임시 파일이 macOS 정리로 삭제

Fix (1 command):
```bash
FILE=/var/folders/vr/p6v6hdc91j7ctshct7nr3fzc0000gn/T/cmux-claude-node-options/restore-node-options.cjs
mkdir -p "$(dirname "$FILE")"
echo "// cmux node-options restore stub" > "$FILE"
```

→ 즉시 패널 정상 작동.

---

## 12. 핵심 파일 인덱스 (재현 시 복사 대상)

```
.cmux/
├── bin/           {say, compact-all, dispatch, await-report, status}
├── prompts/       {cto, be-dev, fe-dev, qa-claude, qa-gemini, reviewer}.md
├── .runtime/      cmux.env (workspace + surface IDs)
├── setup-team-be-fe.sh
└── specs/         (task spec 들 — 신규 프로젝트는 비움)

.claude/
├── rules/         {common, java, python, sql}/*.md
├── agents/        researcher.md + 도메인별 sub-agent
├── contexts/      ubiquitous-language / evolution-story / 도메인
├── skills/        (선택) PDCA + 도메인 skill
└── hooks/         hooks.json (TDD/DDD 강제)

CLAUDE.md          프로젝트 instructions
GEMINI.md          QA-Gemini 룰
docs/
├── 01-plan/
├── 02-design/
├── architecture/  (본 가이드 + blueprint)
└── harness-evolution/   conversation-log.md + 진화 일지

~/.claude/projects/<workspace>/memory/
├── MEMORY.md      인덱스 (auto-load)
└── *.md           user / feedback / project / reference 메모리
```

---

## 13. 검증된 진화 패턴 (Lesson learned)

PillMate 60+ commits 운영하면서 검증된 핵심 패턴:

| 패턴 | 메모리 / 룰 위치 |
|---|---|
| 매 T-task 종료 시 compact-all + conversation-log + TaskUpdate | `feedback_context_compaction` |
| 매 T-task push 직후 Reviewer + QA-Claude + QA-Gemini Option A | `feedback_qa_policy` |
| Researcher 가 spec 작성 전 4섹션 조사 (영향 파일 + 컨벤션 + 깨질 caller + 선례) | `feedback_researcher_reviewer_plan` |
| Plan 승인 게이트 — spec 작성 직후 AskUserQuestion 의무 | 위와 동일 |
| CTO 가 직접 docker exec wget/psql 진단 → 사용자 보고 → spec 재작성 | `feedback_diagnosis_pattern` (검토 중) |
| cmux NODE_OPTIONS 임시 파일 복구 | `feedback_cmux_node_hook_recovery` (검토 중) |
| 사용자 모호점 확인 시 AskUserQuestion 1~2개 (4개 까지) | (CTO 룰) |

---

## 14. 운영 안티 패턴 (검증된 실수)

1. **CTO 가 1줄·핫픽스 직접 수정** — feedback_cto_no_code_edit
2. **compact-all 을 작업 중간에 실행** — 진행 중인 task 가 commit/DONE 누락 발생
3. **DB DELETE/TRUNCATE/DROP** — drug_embeddings 4736건 손실 사고 (2026-05-25)
4. **TimeSlotCards 등 컴포넌트 내부 state 잔존** — props overlay 무력화
5. **versioned migration 으로 매일 SEED** — 1회만 실행 → 다음 날 데이터 누락. R__ Repeatable 사용
6. **transformResponse 에 null guard 없음** — RedBox `Cannot read property data of null`. `response?.data ?? []` 필수
7. **Native SQL 에서 UTC ::date 캐스트** — KST 새벽 데이터 누락. `AT TIME ZONE 'Asia/Seoul'` 명시

상세: `docs/architecture/fe-dose-check-blueprint.md` (PillMate 안정 버전 + 진단 절차)

---

## 15. 한 줄 정리

> 신규 프로젝트에서 본 하네스 재현 = `.cmux` + `.claude` 두 디렉토리 복사 + `setup-team-be-fe.sh` 실행 + Reviewer/Researcher 2 패널 수동 추가 + CLAUDE.md 프로젝트별 조정. 7 패널 × 10단계 PDCA × 자동 메모리 로드.

---

## 변경 이력

| 날짜 | 작성 | 사유 |
|---|---|---|
| 2026-06-01 | CTO | 사용자 명시 "신규 프로젝트에서 현재 하네스 플로우/구조를 똑같이 만들 수 있는 설명 정리" — PillMate 60+ commits 안정 검증 후 재현 가이드 작성 |
