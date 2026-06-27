# CTO 세션 핸드오프 — 2026-06-22

> CTO 재기동 시 이 문서로 즉시 맥락 복원. (전체 대화는 `--resume`로 복원되며, 본 문서는 빠른 오리엔테이션용.)

## 재기동 명령 (권한 안 묻는 모드 + 맥락 이어서)
```
cd /Users/user/Downloads/pillmate
claude --resume 18a3c5bd-459c-463a-9772-edf69f72b58c --dangerously-skip-permissions
```
- 모든 claude(패널 포함)는 `--dangerously-skip-permissions`로 띄운다. (PATH에 claude 없으면 `/Users/user/.nvm/versions/node/v22.14.0/bin/claude`)
- 가장 최근 세션 이어가기는 `claude --continue --dangerously-skip-permissions` 도 가능.

## dev 진행 (오늘 머지, HEAD = 6eb42b6 시점)
- Task 3b: OCR extract-only(`/prescriptions/ocr/extract`) → 검토(review.tsx) → 등록(POST /prescriptions). kdCode/imageKey optional(직접입력·사진없음 등록).
- Task 4: 처방전별 슬롯 조회 API + 홈/스케줄 처방전 단위 표시 + 상세 알림시간 수정(기간종료 잠금).
- 그룹 복약 알림: 60→30초 유예, 본문 "[그룹] {이름}님이 '{약}' 복약…", 탭→그룹상세(/group/{id}), **cross-group 누출 차단**(수신자 schedule.careGroupId 한정, 4경로).
- 약봉투 리네임(UI 문자열만, 코드/도메인 prescription 유지) + 한국어 조사 교정.
- 핀: GroupCard 핀 아이콘(red40, scale 22) on/off 단일핀, 상단고정. 새그룹 CTA 최상단. 홈 "고정 그룹 알림"(핀 그룹 활동, 없으면 핀 유도). 알림함(Bell) 별도 유지.
- Swagger: http://localhost:8080/api/v1/swagger-ui.html

## 배포/환경
- 도커: app_server(:8080), ai_server(:8001), postgres(:5433 user/pw/db=pillmate), redis(:6379). app_server 마지막 재빌드 = 8b44096(이후 FE-only라 백엔드 현행). FE는 Metro(:8081) 리로드.
- 인증 미구현(#99): `X-User-Id` 헤더 dev-stub(UserContextInterceptor). users 1/2/3, group 4="할머니 댁"{1,2,3}. user1만 FCM 토큰.
- Android emulator-5554(FCM 가능). 그룹 dose 알림 e2e 검증 완료(35초, 새 본문, 에뮬레이터 수신).

## cmux 패널 (workspace 611D3972-4CAE-4201-8B37-F4AF1CBE7487)
CTO s3, BE-Dev s7, FE-Dev s6, QA-Claude s4, Reviewer s5, ⚔️Adversarial s1, Researcher s2. UUID는 `.cmux/.runtime/cmux.env`.

## 진행 중 / 남은 일
- #5 후속 P1·P2 (비차단 13건) — 특히 의료 P1 **DDI 병용금기 경고 화면 노출**, extract persist-0 ArchUnit, slots-GET 존재오라클 균일반환 등.
- #99 로그인/인증 (출시 블로커).
- 테스트 데이터(처방전 18~21, 스케줄 10~12, dose_log 76~78, 알림 다수) dev DB에 잔존 — 무해.

## 불변 규칙
- **CTO만 push**, CTO는 코드 직접 수정 X(BE/FE-Dev 디스패치). 변경 커밋 전 **3중 게이트**(Reviewer+QA-Claude+⚔️Adversarial). **DB 삭제 절대 금지**(db-safety). `/clear`·`/compact`는 CTO가 send-panel로 직접.
