# docs/agents — 멀티에이전트 공유 메시지 버스

> CTO ↔ Developer ↔ QA-Sonnet ∥ QA-Gemini 4-에이전트의 모든 작업·보고·판정이 여기에 남는다.

## 디렉토리

```
docs/agents/
├── README.md
├── tasks/        # CTO → 모두 (작업 지시)
├── developer/    # Developer → CTO (구현 보고)
├── qa-sonnet/    # QA-Sonnet → CTO (검증 리포트)
├── qa-gemini/    # QA-Gemini → CTO (검증 리포트)
└── decisions/    # CTO → 모두 (판정·다음 액션)
```

## 파일 명명

- `tasks/{4자리ID}-{slug}.md` — 최초 task
- `tasks/{4자리ID}-iter-{N}.md` — N≥2 재작업 task
- `developer/{4자리ID}-iter-{N}.md`
- `qa-sonnet/{4자리ID}-iter-{N}.md`
- `qa-gemini/{4자리ID}-iter-{N}.md`
- `decisions/{4자리ID}-iter-{N}.md`

ID는 시간 순으로 4자리 zero-pad (0001, 0002, ...).

## 접근 규칙

| 디렉토리 | 쓰기 | 읽기 |
|----------|:----:|:----:|
| `tasks/` | CTO | 모두 |
| `developer/` | Developer | 모두 |
| `qa-sonnet/` | QA-Sonnet | CTO만 (QA-Gemini 읽기 금지) |
| `qa-gemini/` | QA-Gemini | CTO만 (QA-Sonnet 읽기 금지) |
| `decisions/` | CTO | 모두 |

> 두 QA의 독립성을 위해 **서로의 리포트를 읽지 않는다.**

## 절대 규칙

- 환자 식별 정보(PII) 절대 금지 (`.claude/rules/common/medical-safety.md`)
- LLM 응답 원문 그대로 붙여넣기 금지 — 요약·구조화 필수
- 파일은 한 번 생성 후 수정 금지 (감사 추적)
  - 수정이 필요하면 새 iter 파일로

## 참조

- `.cmux/workflows/feature-loop.md`
- `.cmux/README.md`
