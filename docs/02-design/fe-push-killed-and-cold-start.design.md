# 설계: 종료 앱 푸시 표시 + 콜드 스타트 홈 즉시 표시

작성: 2026-07-13 (CTO). 상태: 사용자 승인 대기.

---

## 문제 1 — 앱을 끄면 복약 리마인더 푸시가 안 보임

### 실측 근거 (조사 완료)
| 확인 | 결과 |
|------|------|
| prod 사용자 FCM 토큰 | 등록됨 (users.expo_push_token 142자, provider=FCM) |
| 서버 발송 | `DoseReminderPoller sent=1` 로그 확인, FCM 배치 실패 로그 0건 |
| 발송 페이로드 | notification(title/body) 타입 — data-only 아님 (killed 표시 가능해야 정상) |
| **FE Android 알림 채널** | **생성 코드 0건** (`setNotificationChannelAsync` grep 0) |
| **서버 android 설정** | **channel_id / priority 미지정** (Message에 Notification만) |
| 배치 발송 개별 성공 로그 | 없음 → 단말 도달 여부 서버에서 판별 불가 (관측 갭) |

### 원인 가설 (우선순위)
1. **Android 8+ 채널 부재**: 앱이 채널을 안 만들고 서버도 channel_id 미지정 → Firebase SDK 의 fallback 채널(기본 importance)로 흘러감. 포그라운드는 expo-notifications 핸들러가 직접 그려서 보이지만, 종료 상태 표시는 OS 채널 설정에 의존 → 무음/미표시.
2. OEM 스와이프-킬 정책 (제조사에 따라 recents 제거 = 프로세스 동결 → FCM 차단). 기기 모델 확인 필요.
3. (보조) priority 미지정 시 doze 에서 지연 가능.

### 픽스 설계
**FE (`NotificationsBootstrap`/`setup.ts`) — APK 재빌드 필요**
- 부팅 시 채널 2개 생성:
  - `dose-reminder`: importance MAX(헤드업), 사운드/진동, lockscreenVisibility PUBLIC
  - `group-activity`: importance DEFAULT
- Android 13+ POST_NOTIFICATIONS 권한 흐름은 기존 ensurePushPermission 재사용.

**BE (`FcmSenderAdapter.toMessage`) — 재배포만으로 반영**
- `AndroidConfig`: `channel_id`(알림 타입별 매핑: DOSE_REMINDER→dose-reminder, 그 외→group-activity), `priority=HIGH`.
- 관측 보강: 배치 개별 성공에 `[FCM] sent recipient={} messageId={}` (현재 단건 경로만 로그).

**검증 (verification-evidence)**
- 실기기: 앱 스와이프 종료 → 리마인더 도래 → 시스템 트레이 표시 스크린샷.
- 서버: messageId 로그 + FCM 성공 카운터.
- 미해결 시 2번 가설(OEM) 진단: 같은 시나리오를 다른 기기/에뮬레이터에서 교차.

---

## 문제 2 — 완전 종료 후 재실행 시 홈이 몇 초 비었다가 로딩

### 원인 (확정)
- RTK Query 캐시는 **인메모리 전용** (redux-persist/AsyncStorage 의존성 자체가 없음 — package.json 확인).
- 콜드 스타트 = 캐시 0 → 모든 쿼리 네트워크 왕복 후에야 렌더.

### 설계 — 영속 캐시 + stale-while-revalidate
1. **redux-persist + AsyncStorage** 도입 (MMKV 는 no-overengineering 으로 보류).
2. 부팅 시 rehydrate → **마지막 데이터 즉시 렌더** → 기존 refetchOnMount 가 백그라운드 갱신 → 조용히 교체.
3. persist 대상: api reducer 중 홈 오늘복약·약봉투 목록·그룹 목록·프로필 (whitelist). auth 토큰은 기존 저장소 그대로 (persist 제외).
4. **의료 안전 가드 (필수)**:
   - '오늘 복약' 캐시에 저장 시점 KST 날짜 태그 → 재실행 시 날짜가 다르면 캐시 버리고 스켈레톤 (어제 체크 상태를 오늘 것처럼 보여주면 오복용 유도).
   - 복약 체크 mutation 은 서버가 진실 — stale 표시 상태에서 체크해도 서버 기준 처리(기존 충돌 처리 재사용).
5. 첫 실행(캐시 없음)·날짜 폴백은 빈 화면 대신 **스켈레톤 UI**.
6. persist `version` + migrate 로 응답 스키마 변경 대응 (버전 불일치 시 캐시 폐기 — 안전 기본값).

### 검증
- 앱 완전 종료 → 재실행 → 홈에 이전 데이터 **즉시**(<0.5s) 표시 + 몇 초 뒤 최신으로 교체 (녹화/스크린샷).
- 날짜 경계: 기기 날짜 +1일 변경 후 재실행 → 스켈레톤 폴백 확인.
- regression: 홈 복약 체크 여전히 동작 (scope-discipline 핵심 여정).

---

## 실행 계획 (승인 시)
| 순서 | 태스크 | 담당 | 재빌드 |
|------|--------|------|--------|
| 1 | BE: AndroidConfig(channel/priority) + FCM 개별 성공 로그 | BE-Dev | 재배포만 |
| 2 | FE: 알림 채널 생성 | FE-Dev | **APK 재빌드** |
| 3 | FE: redux-persist 콜드 캐시 (+스켈레톤·날짜 가드) | FE-Dev | 위와 같은 빌드에 포함 |
| 4 | 실기기 검증 (종료 푸시 + 콜드 스타트) | 사용자+CTO | — |

QA-Tier: 1 (푸시=의료 경로, 캐시=복약 표시 정확성).
