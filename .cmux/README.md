# PillMate Team Orchestra (CMUX 5-패널)

> CTO(Opus) ↔ Dev(Sonnet) ↔ QA-Claude(Sonnet) ∥ QA-Gemini(2.5-Pro) + Reconciler
> 한 워크스페이스 안 5개 패널이 파일 큐(`.cmux/messages/`)로 협업한다.

## 패널 레이아웃

```
┌────────────────────┬────────────────────┐
│ CTO (이 패널)       │  DEV               │
├────────────────────┼────────────────────┤
│ QA-CLAUDE          │  QA-GEMINI         │
├────────────────────┴────────────────────┤
│ RECONCILER (전체 폭)                     │
└─────────────────────────────────────────┘
```

## 디렉토리

```
.cmux/
├── README.md                   # 본 문서
├── setup-cmux.sh               # 현재 패널을 CTO로, 나머지 4 패널 자동 생성·기동
├── teardown-cmux.sh            # 비-CTO 패널 일괄 종료
├── lib/
│   ├── common.sh               # 메시지 큐 helpers (sourced)
│   └── reconciler.sh           # 두 QA verdict 통합 → CTO inbox
├── roles/
│   ├── dev.sh                  # Dev 패널 워처 (claude -p)
│   ├── qa-claude.sh            # QA-Claude 워처 (claude -p)
│   └── qa-gemini.sh            # QA-Gemini 워처 (gemini -p)
├── bin/
│   ├── dispatch                # CTO → Dev: task 디스패치
│   ├── await-report            # CTO: reconciled 리포트 대기
│   └── status                  # 큐 깊이 출력
├── prompts/
│   ├── cto.md                  # CTO 시스템 프롬프트
│   ├── dev.md
│   ├── qa-claude.md
│   └── qa-gemini.md
├── workflows/
│   └── feature-loop.md         # 인간 가독 워크플로우 명세
├── messages/                   # (gitignore) 런타임 inbox/outbox
├── logs/                       # (gitignore) 처리 로그
├── specs/                      # (gitignore) CTO가 발행한 task spec
└── .runtime/cmux.env           # (gitignore) 패널 surface 참조
```

## 초기 사용

### 1. 사전 확인
- `cmux ping`            → CMUX 앱이 실행 중
- `claude --version`     → Claude Code CLI
- `gemini --version`     → Gemini CLI (`@google-ai/gemini-cli` 또는 호환)
- `export GEMINI_API_KEY=...` (필요 시 Gemini CLI가 별도 설정 사용)

### 2. CTO 패널에서 셋업
```bash
cd /Users/user/Downloads/pillmate
./.cmux/setup-cmux.sh
```
- 이 패널이 CTO 패널이 된다 (현재 focus 기준)
- 나머지 4개 패널이 자동 생성되고 각자의 watcher 스크립트가 기동된다
- `.cmux/.runtime/cmux.env` 에 surface 참조가 저장됨

### 3. 첫 task 디스패치
CTO 패널(이 패널)에서:
```bash
# spec 작성
mkdir -p .cmux/specs
cat > .cmux/specs/T001.md <<'EOF'
# T001: prescription 컨텍스트 OCR 업로드 URL 발급 API

## DoD
- [ ] presentation: POST /prescriptions/upload-url → presigned URL 반환
- [ ] application: GetUploadUrlUseCase (인터페이스 + Service)
- [ ] domain: 변경 없음 (이미 존재)
- [ ] infrastructure: S3PresignedUrlAdapter
- [ ] TDD: RED→GREEN→REFACTOR

## Bounded Context
prescription

## 적용 규칙
- .claude/rules/java/spring-boot.md
- .claude/rules/java/junit.md
- .claude/rules/common/medical-safety.md (객체 키에 환자식별자 금지)

## 테스트 힌트
- 빈 careGroupId → 400
- 만료시간 5분 검증
EOF

./.cmux/bin/dispatch T001 .cmux/specs/T001.md
./.cmux/bin/await-report T001        # reconciled 리포트가 도착할 때까지 대기
```

### 4. 종료
```bash
./.cmux/teardown-cmux.sh             # Dev/QA/Reconciler 패널 종료. CTO 패널은 유지.
```

## 메시지 흐름

```
                                     ┌───────────────────────────┐
                                     │ messages/dev/inbox/       │
   CTO ─dispatch─►─────────────────►─┤  (task JSON + spec path)  │
                                     └────────────┬──────────────┘
                                                  │
                                          dev.sh watcher
                                                  │ claude -p (acceptEdits)
                                                  ▼
                              messages/dev/outbox/<id>.json
                                                  │
                ┌─────────────────────────────────┴──────────────────────────────┐
                ▼                                                                ▼
   messages/qa-claude/inbox/<id>.json                          messages/qa-gemini/inbox/<id>.json
                │                                                                │
        qa-claude.sh                                                     qa-gemini.sh
        (claude -p)                                                      (gemini -y -p)
                │                                                                │
   messages/qa-claude/outbox/<id>.json                          messages/qa-gemini/outbox/<id>.json
                │                                                                │
                └──────────────────► messages/reconcile/pending/ ◄───────────────┘
                                                  │
                                          reconciler.sh
                                                  │
                                     messages/cto/inbox/<id>-*.json
                                                  ▲
                                 await-report ────┘
```

## 절대 규칙

- 환자 식별 정보(PII)를 spec/메시지/로그에 절대 포함 금지 (`.claude/rules/common/medical-safety.md`)
- Phase 1 범위 준수 — MSA/Kafka/Outbox 도입 금지 (`.claude/rules/common/no-overengineering.md`)
- Dev 패널만 코드 수정. QA는 read-only + 리포트만.
- 커밋·푸시는 CTO가 사용자 승인을 받은 후에만 (`.claude/skills/commit-convention.md`)
- 두 QA는 서로의 리포트를 보지 않는다 (Reconciler만 종합)

## 참조

- `.cmux/workflows/feature-loop.md` — 루프 명세
- `.cmux/prompts/*.md` — 각 역할의 시스템 프롬프트
- `CLAUDE.md`, `.claude/rules/` — 프로젝트 불변 규칙
