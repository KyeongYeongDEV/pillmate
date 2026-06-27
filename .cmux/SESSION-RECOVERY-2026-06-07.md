# Session Recovery — 2026-06-07 13:36

> cmux 재시작 안전망. 본 파일 + 백업 디렉터리 + ~/.claude jsonl 3중 보존.

## 백업 위치

```
/Users/user/Downloads/pillmate-session-backup-2026-06-07-1336/
├ 0c498681-312a-4eec-af3e-ac0fcf6c66e2.jsonl  (3.6M) — QA-Claude
├ 18a3c5bd-459c-463a-9772-edf69f72b58c.jsonl  ( 28M) — CTO ★ 메인 대화
├ 95060d2e-bb87-4b4e-ba79-093b1d195660.jsonl  ( 28M) — CTO 보조 또는 FE-Dev 관련
├ 9bd56991-3658-4473-bebf-9197e41a676a.jsonl  (2.6M) — Researcher
├ cd60988b-8696-47f0-870a-0a8d1f922912.jsonl  (1.7M) — Reviewer
├ ecda4724-11a6-4aaf-8221-11b27d23ef9c.jsonl  ( 66M) — CTO 이전 + Reviewer 컨텍스트
├ cmux-messages/                              — inbox/outbox 디스크 메시지
├ cmux.json                                   — cmux 앱 워크스페이스 설정
└ memory/                                     — 사용자 메모리 (MEMORY.md + 13 entry)
```

총 131M.

## 세션 → 역할 매핑 (오늘 활성 6건)

| Session UUID | 크기 | 역할 추정 | 단서 |
|---|---|---|---|
| `18a3c5bd-459c-463a-9772-edf69f72b58c` | 28M | **CTO ★ (메인 대화)** | CTO 2566 / BE-Dev 1139 / Reviewer 1006 |
| `95060d2e-bb87-4b4e-ba79-093b1d195660` | 28M | CTO 보조 또는 FE-Dev | be-dev.md + fe-dev.md 균등 |
| `ecda4724-11a6-4aaf-8221-11b27d23ef9c` | 66M | CTO 이전 세션 (BE/Reviewer 컨텍스트 다수) | CTO 548 / Reviewer 56 |
| `cd60988b-8696-47f0-870a-0a8d1f922912` | 1.7M | Reviewer | reviewer.md 13 매치 |
| `9bd56991-3658-4473-bebf-9197e41a676a` | 2.6M | Researcher | researcher.md 17 매치 |
| `0c498681-312a-4eec-af3e-ac0fcf6c66e2` | 3.6M | QA-Claude | QA-Claude 142 매치 |

cmux 실행 중이던 surface: **33, 34, 35, 39, 40** (5개)

## 재시작 절차

### Step 1 — cmux 앱 Quit
```
cmux 메뉴 → Quit (⌘Q)  또는  Dock 우클릭 → 종료
```

### Step 2 — cmux 앱 다시 열기
- 워크스페이스 `9C897235-2EFA-4AF4-BF8C-03728A9E727B` 자동 복원
- 5개 surface (33/34/35/39/40) 레이아웃 복원

### Step 3 — 각 패널의 Claude 세션 복원
cmux 가 자동으로 마지막 세션을 resume 하지 않으면, 각 패널에서:
```bash
claude --continue          # 가장 최근 세션 자동
# 또는 명시적으로
claude --resume <session-uuid>
```

UUID 매칭 가이드:
- **CTO 패널** → `--resume 18a3c5bd-459c-463a-9772-edf69f72b58c`
- **CTO 보조 (FE-Dev 멘션 多)** → `--resume 95060d2e-bb87-4b4e-ba79-093b1d195660`
- **Reviewer** → `--resume cd60988b-8696-47f0-870a-0a8d1f922912`
- **Researcher** → `--resume 9bd56991-3658-4473-bebf-9197e41a676a`
- **QA-Claude** → `--resume 0c498681-312a-4eec-af3e-ac0fcf6c66e2`

### Step 4 — inbox 큐 자동 픽업 확인
```bash
ls .cmux/messages/dev/inbox/
# 비어있어야 정상 (BE-Dev 가 픽업 완료)
# 비어있지 않으면 BE-Dev 패널에 say 또는 dispatch 재실행
```

### Step 5 — Node stub 정상 확인 (재발 방지)
```bash
ls /var/folders/vr/p6v6hdc91j7ctshct7nr3fzc0000gn/T/cmux-claude-node-options/
# restore-node-options.cjs 존재해야 정상
```

## 현재 진행 중 작업 (TaskList snapshot 2026-06-07 13:36)

| # | Task | 상태 | 패널 |
|---|---|---|---|
| 69 | T-CONTEXT-COMPACT-DISCIPLINE | in_progress | CTO 영구 룰 |
| 99 | T-USER-AUTH (SSO) | pending | MVP 마지막 |
| 107 | T-NOTIFY-EXPO-PUSH (umbrella) | in_progress | sub #109/#110 완료, #111 만 남음 |
| 111 | T-BE-NOTIFY-TRIGGERS | in_progress | BE-Dev — **재시작 후 시작 X 가능성** |
| **115** | **T-AI-OCR-RAG-EVAL-GT** | in_progress | **BE-Dev inbox 대기 ★ 우선** |
| 116 | T-CMUX-NODE-STUB-REGEN | completed | 인프라 fix |

## 인프라 상태

- ✅ Node stub `/var/folders/.../T/cmux-claude-node-options/restore-node-options.cjs` 재생성됨
- ⛔ Gemini CLI 여전히 INVALID_ARGUMENT (QA-Gemini disabled)
- ⛔ BE-Dev 패널 surface:33 에서 옛 T006d wait 루프 잔존 (재시작 시 깔끔)

## 백업 검증 명령

```bash
# 백업 무결성 확인
ls -lh /Users/user/Downloads/pillmate-session-backup-2026-06-07-1336/
du -sh /Users/user/Downloads/pillmate-session-backup-2026-06-07-1336/   # → 131M

# 복원 시 (만약 ~/.claude jsonl 손상되면)
cp /Users/user/Downloads/pillmate-session-backup-2026-06-07-1336/*.jsonl \
   /Users/user/.claude/projects/-Users-user-Downloads-pillmate/
```

## 미커밋 변경 (재시작 후에도 git 보존)

```
M .claude/settings.local.json
M .cmux/bin/say
M .cmux/prompts/cto.md
M back/app_server/src/test/java/com/pillmate/prescription/domain/model/PrescriptionTest.java
?? .bkit/  .claude/agents/researcher.md  .cmux/bin/{clear-all,compact-all}
?? .cmux/prompts/reviewer.md  back/.drug_image_cache_*
?? docs/.pdca-snapshots/  docs/architecture/  docs/harness-evolution/
?? front/.claude/  front/docs/screenshots/group-home-tab.png  front/tests/tests/
```

손실 위험 0 (filesystem). 추가 안전 원하면:
```bash
git stash push -m "before-cmux-restart-2026-06-07" -u
# 복원: git stash pop
```

## 변경 이력

| 시각 | 이벤트 |
|---|---|
| 2026-06-07 13:31 | Node MODULE_NOT_FOUND 진단 (CTO #116) |
| 2026-06-07 13:31 | Node stub 재생성 + smoke test PASS |
| 2026-06-07 13:11 | #115 T-AI-OCR-RAG-EVAL-GT BE-Dev 디스패치 |
| 2026-06-07 13:36 | 본 백업 디렉터리 생성 (131M) |
