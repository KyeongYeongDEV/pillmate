---
name: commit-convention
description: PillMate 커밋 메시지/브랜치 전략 강제 스킬. 코드 수정·파일 생성 시 태그 기반 커밋 메시지와 매칭되는 브랜치 전략을 자동 적용한다. 커밋·푸시 전 주인 승인 필수.
---

# Commit & Branch Convention

## 목적

PillMate 저장소의 모든 변경은 **태그 기반 커밋 메시지**와 **태그-매칭 브랜치 전략**을
따른다. 이 스킬은 코드를 수정하거나 파일을 생성할 때마다 규칙을 강제한다.

---

## 절대 규칙

1. **커밋·푸시 전 주인(사용자) 승인 필수**
   - 메시지 초안 → 승인 요청 → 승인 후 `git commit` → `git push`
   - 푸시 실패 시 원인 분석 → 승인 재요청 → 재실행

2. **메시지 포맷**: `Tag(space):(space)제목`
   - 도메인 명시가 필요하면: `Tag(domain) : 제목`
   - 한 줄 요약 (최대 70자)
   - 본문 추가 금지 (필요 시 별도 PR description)

3. **브랜치 전략**: 모든 작업은 `main`이 아닌 태그-매칭 브랜치에서 수행
   - 작업 시작 전 브랜치 생성 및 체크아웃
   - 작업 종료 후 머지/PR은 주인 승인 하에서만

---

## 태그 카탈로그

| 태그               | 설명                                                              | 브랜치 prefix |
|--------------------|-------------------------------------------------------------------|---------------|
| `Feat`             | 새로운 기능 추가                                                  | `feat/`       |
| `Fix`              | 버그 수정                                                         | `fix/`        |
| `Design`           | CSS 등 사용자 UI 디자인 변경                                      | `design/`     |
| `!BREAKING CHANGE` | 커다란 API 변경                                                   | `breaking/`   |
| `!HOTFIX`          | 치명적 버그 긴급 수정                                             | `hotfix/`     |
| `Style`            | 코드 포맷/세미콜론 등, 동작 변경 없음                             | `style/`      |
| `Refactor`         | 프로덕션 코드 리팩토링                                            | `refactor/`   |
| `Comment`          | 주석 추가/변경                                                    | `comment/`    |
| `Docs`             | 문서 수정                                                         | `docs/`       |
| `Test`             | 테스트 코드 추가/리팩토링 (production code 변경 없음)             | `test/`       |
| `Chore`            | 빌드, 패키지 매니저 등 (production code 변경 없음)                | `chore/`      |
| `Rename`           | 파일/폴더명 수정·이동                                             | `rename/`     |
| `Remove`           | 파일 삭제만 수행                                                  | `remove/`     |

---

## 브랜치 명명 규칙

```
{prefix}/{도메인}-{짧은-설명}
```

예시:
- `feat/prescription-ocr-upload`
- `fix/schedule-duplicate-check`
- `refactor/drug-repository-split`
- `docs/commit-convention-skill`
- `chore/gradle-dependency-bump`

**Bounded Context가 명확하면** 도메인 segment를 포함:
- `user`, `caregroup`, `prescription`, `drug`, `schedule`, `doselog`, `common`, `infra`, `ai-server`, `docs`

---

## 워크플로우

### 단계 0: 작업 시작 전
- [ ] 현재 브랜치 확인 (`git branch --show-current`)
- [ ] `main`이라면 새 브랜치 생성 (`git switch -c {prefix}/{name}`)
- [ ] 기존 작업 브랜치라면 태그가 작업 성격과 일치하는지 확인 (다르면 새 브랜치)

### 단계 1: 변경 작성
- [ ] 코드/문서 수정
- [ ] TDD 사이클 적용 (도메인/유스케이스 코드라면)
- [ ] ArchUnit/테스트 통과 확인

### 단계 2: 커밋 메시지 초안
- [ ] 변경 성격에 맞는 태그 선택
- [ ] 도메인 명시가 도움 되면 `Tag(domain) : 제목`
- [ ] 한 줄 요약, `:` 앞뒤 공백 1칸씩

### 단계 3: 주인 승인 요청
- [ ] 변경된 파일 목록 + 커밋 메시지 초안 제시
- [ ] 승인 응답 대기 (yes/no)
- [ ] 거부 시 수정 후 재요청

### 단계 4: 커밋 & 푸시
- [ ] `git add` (특정 파일만, `-A`는 지양)
- [ ] `git commit -m "Tag : 제목"`
- [ ] `git push -u origin {branch}` (최초) 또는 `git push`
- [ ] `git status`로 결과 검증

### 단계 5: 실패 처리
- [ ] 푸시 실패 시 원인 (권한, 충돌, hook 등) 보고
- [ ] 해결 방안 + 재실행 승인 요청
- [ ] 승인 후 재시도

---

## 좋은 예 / 나쁜 예

### Good

```
Feat(prescription) : OCR 업로드 Pre-signed URL 발급 API 추가
Fix(schedule) : 같은 시간대 중복 스케줄 검증 누락 수정
Refactor(drug) : DrugRepository를 read/write 인터페이스로 분리
Docs : 커밋 컨벤션 스킬 추가
Chore : Spring Boot 3.3.5로 업그레이드
```

### Bad

```
feat: ocr 추가              # 소문자 태그, `:` 앞 공백 누락
Feat:ocr 추가               # 공백 누락
[Feat] OCR 추가             # 대괄호 형식 금지
Feat - OCR 추가             # `-` 대신 `:` 사용
Update OCR                  # 태그 누락
```

---

## 안티 패턴

- 한 커밋에 여러 태그 성격 섞기 → 분할
- `git add -A`로 의도치 않은 파일 포함 → 명시적 add
- 주인 승인 없이 커밋/푸시
- `main`에 직접 커밋
- `--no-verify`로 hook 우회
- 본문에 환자 식별 정보 포함 (`rules/common/medical-safety.md`)

---

## 참조

- `rules/common/tdd-cycle.md` — 커밋 분리 원칙
- `rules/common/medical-safety.md` — 메시지/로그 PII 금지
- `commands/pill-arch-check.md` — 커밋 전 검증
