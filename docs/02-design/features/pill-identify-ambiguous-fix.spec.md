# T-PILL-IDENTIFY-AMBIGUOUS-FIX — pill_identify SQL AmbiguousParameterError 해소

작성일: 2026-06-28

## 진단

ai_server 로그 다수 발생:
```
WARNING app.rag.ocr.pill_identify :: pill_identify DB error: AmbiguousParameterError
```

→ OCR 후 약 식별 단계 SQL 쿼리에서 **named parameter 모호성** 발생. asyncpg/SQLAlchemy의 named param 충돌. 500/PILL_999 응답 일부 원인.

## 작업

### 1. Root cause 파악
파일: `back/ai_server/app/rag/ocr/pill_identify.py`
- 사용 중인 SQL 쿼리 점검 (named param 중복 / 같은 이름 + 다른 위치 등)
- asyncpg 의 경우 `$1, $2` positional이라 named param 사용 시 wrap 필요
- SQLAlchemy `text(":name")` 사용 시 같은 :name 여러 곳 사용은 OK이지만 type 불일치 시 모호

### 2. Fix
- positional ($1, $2) 로 변경 (asyncpg 직접 사용 시) 또는
- named param 이름 unique 화 (`:drug_name1`, `:drug_name2` 등) 또는
- ORM (SQLAlchemy) 의 안전한 binding 사용
- 기존 로직 변경 X (입력/출력 무변경)

### 3. 테스트
- `back/ai_server/tests/test_pill_identify.py` (있으면 갱신, 없으면 NEW)
  - 회귀 케이스: 실제 쿼리 호출 → AmbiguousParameterError 발생 안 함
  - 같은 입력 반복 호출 안전
  - edge: 빈 입력, 단일/다중 약 입력
- pytest 회귀 0

### 4. 인수
1. `pill_identify.py` SQL 쿼리에서 AmbiguousParameterError 발생 안 함
2. ai_server e2e 호출 시 로그에 해당 WARNING 0건
3. 매칭 결과 무변경 (회귀 X)
4. pytest 통과

### 5. 보고
`.cmux/messages/cto/inbox/T-PILL-IDENTIFY-AMBIGUOUS-FIX-be-done.json`
포함: 진단 + 수정 SQL diff + pytest + e2e 로그 확인 + git status

## 규칙

- BE only (ai_server)
- git commit/push 금지 (CTO 단독)
- DB-safety: SELECT 또는 안전한 named/positional param만 (DELETE/TRUNCATE/DROP 0)
- TDD: 회귀 테스트 RED → GREEN
- clean-code SRP, 매직넘버 X
- no-overengineering: 쿼리 구조 변경 최소
