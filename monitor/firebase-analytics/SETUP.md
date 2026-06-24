# Firebase Analytics (모바일 분석) — 무료

> Google Analytics for Firebase는 **완전 무료**(이벤트 무제한, 표준 리포트). 우리는 이미 Firebase(FCM) 사용 중이라 추가 비용 0.
> 무료가 아닌 것(쓰지 말 것): BigQuery 대량 export(쿼리 과금), GA360. 표준 콘솔 리포트만 쓰면 $0.
> ★의료 앱: 이벤트 파라미터에 ★환자정보·약품명·처방내용·건강상태 절대 금지(개인정보/민감정보).

## 1. 무료 범위 확인
- 이벤트 수집·기본 대시보드(활성 사용자/리텐션/퍼널): **무료**
- 안 쓸 것: BigQuery linking(쿼리 비용 발생), 광고 SDK
→ 콘솔에서만 보면 $0 유지

## 2. Expo(React Native) 연동
Expo는 `@react-native-firebase/analytics` 사용(이미 FCM으로 firebase 설정돼 있으면 google-services 재사용).
```bash
npx expo install @react-native-firebase/app @react-native-firebase/analytics
```
- `app.json`에 google-services.json / GoogleService-Info.plist 경로(FCM 설정 시 이미 있을 가능성).
- ★Expo Go 불가 → dev client/EAS 빌드 필요(이미 custom dev client 쓰는 중).

## 3. 안전한 이벤트 설계 (PII 금지)
```ts
import analytics from '@react-native-firebase/analytics';

// 좋음 — 식별정보 없음
await analytics().logEvent('prescription_register_success', { drug_count: 4, source: 'ocr' });
await analytics().logEvent('dose_check', { slot: 'morning' });
await analytics().logScreenView({ screen_name: 'prescription_detail' });

// ★금지 — 절대 넣지 말 것
// { patient_name, drug_name, prescription_text, disease, user_email ... }
```
- userId 설정 시 `setUserId(해시값)` 만(원문 식별자 X). 건강/약 정보는 파라미터에 금지.

## 4. 무엇을 볼 수 있나
- DAU/WAU/MAU, 리텐션(N일 재방문), 화면별 체류, 퍼널(촬영→OCR→등록 전환율), 버전별 분포.
- → DAU/MAU는 서버측 자체집계(`/admin/stats`)와 교차검증용으로도 좋음.

## 5. 대안 (Firebase 안 쓰고 싶으면)
- PostHog Cloud 무료 티어(이벤트 1M/월) — 단 또 다른 벤더. 이미 Firebase 있으니 FA가 비용·통합상 유리.

## 체크리스트
- [ ] BigQuery export 끄기(무료 유지)
- [ ] 이벤트 파라미터 PII·건강정보 0
- [ ] setUserId는 해시만
- [ ] EAS/dev client 빌드에 포함(Expo Go 불가)
