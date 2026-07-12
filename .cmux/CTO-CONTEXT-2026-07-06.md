# CTO 핸드오프 2026-07-06 (큐된 /clear 오발사 대비 보험)

## 사고 경위
- `say fe` 디스패치가 stale surface UUID 때문에 **CTO 자신의 패널**에 배달됨
- CTO 세션 큐에 `/clear` + T-FE-KAKAO 태스크 메시지 대기 중 → 턴 종료 시 /clear 실행 위험
- cmux.env 의 FE_DEV_SURFACE(4A34AC42)가 CTO 패널로 해석됨 (재시작 후 재매핑 필요)
- **교훈: say 전에 반드시 `cmux tree --id-format both` 로 UUID 실검증**

## 진행 중 작업 상태
1. **T-FE-KAKAO-BTN-HEIGHT-RUNTIME** (스펙: .cmux/specs/T-FE-KAKAO-BTN-HEIGHT-RUNTIME.md)
   - 카카오 로그인 버튼 실측 20dp — minHeight(112)/padding 런타임 완전 무시
   - stale bundle 아님 확정: Metro 번들에 112 포함(직접 curl 검증) + 앱 pid 5312 localhost:8081 연결 확인 + 완전재시작 후에도 20dp
   - 6/28 커밋 3개(95ab29b, 98d1461, 5aa9317)가 전부 미적용이었음 → FE-Dev 재디스패치 필요 (아직 정상 전달 안 됨!)
2. **법적 문서 초안 6종 완료**: docs/legal/ (README, 개인정보처리방침, 민감정보처리동의, 국외이전동의, 이용약관, 환불정책)
3. **진짜 카카오 SSO**: 사용자에게 콘솔 설정 절차 안내 완료. 대기: Client Secret + Redirect URI 등록
   - BE .env에 KAKAO_CLIENT_SECRET / KAKAO_REDIRECT_URI 추가 필요, FE .env.local에 EXPO_PUBLIC_KAKAO_* 추가 + Metro 재시작
4. **로컬 서버**: 전부 healthy. 발견 버그 2건 미처리:
   - WeeklyReportScheduler: health_reports.care_group_id NOT NULL 위반 (7/5 00:00, 2건 실패)
   - 미존재 경로가 404 대신 500 PILL_999 (NoResourceFoundException 미매핑)
5. **미검증 in-flight**: T-BE-WITHDRAW/T-FE-WITHDRAW (reactivateIfWithdrawn 코드는 확인됨), T-PII-SSN-BLOCK, T-BE-DRUG-IMAGE-URL-CACHE

## 다음 액션
1. cmux.env FE_DEV_SURFACE 실제 UUID 재확인 후 T-FE-KAKAO 디스패치
2. 카카오 SSO STEP 2~4 (사용자 Client Secret 제공 대기)
