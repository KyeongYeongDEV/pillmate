# 하네스 진화 — Researcher + Reviewer + Plan 게이트 도입

**일자**: 2026-05-27
**계기**: 사용자 (포폴 작성 중) 가 외부 자료 (Human-in-the-Loop / AI 역할 분리 / 프롬프트 로그) 3원칙 참고하여 PillMate 부족점 보강 요청

---

## 비교표 — Before / After

| 영역 | Before (2026-05-26) | After (2026-05-27) | 변경 사유 |
|---|---|---|---|
| **코드 조사 단계** | CTO 가 spec 작성 시 직접 grep/Read | **Researcher agent** Task() 호출 → spec 의 "Researcher 조사 결과" 섹션에 인용 | 조사 단계 명시화 + CTO 컨텍스트 절약 + 재현성 |
| **spec 의무 섹션** | DoD / Context / 룰 / 변경 파일 / 테스트 힌트 | + **작업 순서 / Risk(P0/P1/P2) / 의존성 / Researcher 조사 결과** | Risk 사전 파악 (HITL 1원칙) |
| **Plan 승인 게이트** | push 만 사용자 승인. spec 작성 후 바로 `say` 디스패치 (AskUserQuestion 일부만) | **모든 spec 작성 직후 AskUserQuestion 의무** — 명시 승인 후에만 say | 무작정 구현 방지 |
| **검증 패널** | QA-Claude + QA-Gemini | + **Reviewer (surface:42)** 신규 | QA(동작) vs Reviewer(품질) 영역 분리 |
| **Reviewer 권한** | (없음 — QA-Claude 가 일부 흡수) | **READ-ONLY 절대 룰** (Edit/Write/Bash modify 금지) | 코드 수정은 Dev 만 (CTO 룰과 동일 원칙) |
| **QA-Claude prompt** | "코드 관점 (TDD·DDD·clean-code) + 실행 검증" 혼합 | "**실행 시 동작** (테스트 PASS, 의료 안전)" 명확화 | 코드 정적 품질은 Reviewer 가 흡수 |
| **task 흐름** | 6단계 (요구→spec→dispatch→리포트→검토→푸시) | **10단계** (요구→Researcher→spec→Plan 승인→Dev→push→Reviewer→QA→리포트 종합→Notion) | HITL + 역할 분리 + 로그 |
| **CTO 영역** | spec / rules / memory / git push / docker ops | (동일 유지) | 룰 변경 없음 |
| **로그/문서화** | Notion 진행 현황 + git log | (동일) + **본 문서 같은 하네스 진화 일지 신규** | 의사결정 기록 + 재현성 |

---

## 변경 파일

### 신규
- **`.claude/agents/researcher.md`** — Researcher agent 시스템 프롬프트
- **`.cmux/prompts/reviewer.md`** — Reviewer 패널 시스템 프롬프트
- **`docs/harness-evolution/2026-05-27_researcher_reviewer_plan_gate.md`** (본 문서)
- **`docs/harness-evolution/conversation-log.md`** (대화 기록, 별도)

### 갱신
- **`.cmux/prompts/cto.md`**
  - "한 task의 흐름" 6 → 10단계 확장
  - "패널 구성" 테이블 신규 (Reviewer surface:42 포함)
  - Plan 승인 게이트 의무 명시
  - Researcher 호출 의무 명시
- **메모리** (`MEMORY.md` 인덱스 + `feedback_researcher_reviewer_plan.md` 신규 예정)

---

## 새 task 흐름 (10단계)

```
1. 사용자 요구 (AskUserQuestion 으로 모호점 해소)
   ↓
2. Researcher Task() 호출 — 영향 파일 + 컨벤션 + 깨질 caller + 선례
   ↓
3. spec 작성 (.cmux/specs/T-XXX.md) — Researcher 결과 + DoD + 순서 + Risk + 의존성
   ↓
4. 🚨 Plan 승인 게이트 — AskUserQuestion ("그대로/수정/중단")
   ↓  (승인 후에만)
5. say be / say fe — Dev 디스패치
   ↓
6. DONE 시그널 → 사용자 승인 → git push origin dev
   ↓
7. Reviewer 디스패치 (surface:42) — 코드 정적 품질 + 룰 line 단위
   ↓
8. QA-Claude + QA-Gemini 디스패치 (Option A 의무, 양쪽)
   ↓
9. 리포트 종합 — Reviewer P0 + QA verdict
   ↓
10. Notion 진행 현황 업데이트 + compact-all
```

---

## 외부 자료 3원칙 → PillMate 매핑

| 외부 자료 원칙 | PillMate 적용 |
|---|---|
| ① 작업 계획 + 승인 (HITL) | Plan 승인 게이트 (4단계) + spec 의무 섹션 (Risk/순서) |
| ② AI 역할 분리 (리서처·플래너·리뷰어) | Researcher agent + CTO 가 플래너 흡수 + Reviewer 신규 패널 |
| ③ 프롬프트/도구 로그 | Notion 자동 문서화 + 본 evolution 일지 + 대화 로그 |

→ "AI 시대에 걸맞은 안전하고 체계적인 개발 프로세스 직접 설계" 핵심 어필 가능.

---

## QA vs Reviewer — 영역 분리 (중요)

| 차원 | Reviewer (신규) | QA-Claude | QA-Gemini |
|---|---|---|---|
| 관점 | 코드 정적 품질 | 실행 시 동작 | 정밀 실행 |
| 검증 대상 | SOLID / naming / clean-code / 룰 line | 테스트 PASS / 의료 안전 / ArchUnit | docker healthy / curl / DB query |
| 시점 | push 직후 (또는 직전) | push 직후 | push 직후 |
| 출력 | line 단위 P0/P1/P2 리포트 | "동작 OK + 이슈 우선순위" | "정밀 검증 + DB 상태" |
| 권한 | READ-ONLY (Edit/Write 절대 X) | READ-ONLY | READ-ONLY (SELECT 만) |
| 모델 | Sonnet 또는 Gemini (선택) | Sonnet | Gemini 2.5 Pro |

→ 영역 겹치지 않음. 같은 task 라도 각자 다른 보고.

---

## 사용자가 직접 처리할 행동 (CTO 권한 밖)

1. **cmux 에서 surface:42 (Reviewer) 패널 신규 생성**
   - CTO 가 panel 자체를 생성할 수 없음
   - 사용자가 cmux UI 에서 새 패널 추가 + `.cmux/prompts/reviewer.md` 를 시스템 프롬프트로 지정
2. **Researcher 는 패널 X** — CTO 가 `Task(researcher)` 도구로 호출 (Claude Code sub-agent)

---

## 후속 task 후보

- **T-RULES-EXTERNAL-ASYNC** — Spring 외부 호출 WebClient + Mono 전환 (사용자 결정 완료, 대기 중)
- **T-RULES-PLAN-GATE-RETROFIT** — 기존 진행 중 task (T-DOMAIN-PIVOT 등) 도 Plan 승인 의무 적용
- **Reviewer 도입 첫 시점** — T-DOMAIN-PIVOT 또는 다음 push 부터

---

## 참고

- `.claude/rules/common/no-overengineering.md` — Phase 1 MVP 규모 적합 검토 (Researcher/Reviewer 도입은 OK, 별도 Planner agent 는 X)
- `feedback_cto_no_code_edit.md` — Reviewer 룰의 read-only 원칙은 CTO 룰과 같은 기조
- 외부 자료 (사용자 제공 이미지) — HITL / AI 역할 분리 / 프롬프트 로그 3원칙
