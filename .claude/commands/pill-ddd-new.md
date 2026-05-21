---
name: pill-ddd-new
description: 새 Bounded Context의 4-레이어 골격을 생성한다.
target: skills/ddd-bootstrap.md
---

# /pill-ddd-new

## 사용법

```
/pill-ddd-new {context-name} "{한 줄 설명}"
```

예시:
```
/pill-ddd-new notification "복용 알림과 그룹 알림 발송"
/pill-ddd-new report "월별 복용 리포트와 건강 추천"
```

## 실행 흐름

1. 컨텍스트 이름 검증 (영문 소문자, 단수형)
2. Ubiquitous Language 등록 확인
3. `skills/ddd-bootstrap.md` 호출
4. 디렉터리 + 기본 클래스 골격 생성
5. ArchUnit 의존 규칙 자동 추가
6. 다음 단계 안내 (`/pill-tdd`로 Aggregate Root 작성)

## 출력 파일

```
src/main/java/com/pillmate/{context}/
  presentation/  application/  domain/  infrastructure/
src/test/java/com/pillmate/{context}/
  (동일 구조)
```
