# EAS Build — Android Dev Client APK 가이드

> 2026-06-03 — Apple Dev 미가입 + iOS Expo Go SDK 53+ 의 expo-notifications 제거 이슈 우회.
> Android dev client APK 빌드 → push 알림 + LAN API 정상 동작.

## 사전 준비

```bash
cd front
npm install -g eas-cli       # 이미 설치되어 있으면 skip
eas login                    # Expo 계정 (https://expo.dev)
```

## .env.local 확인

빌드 전 LAN IP 가 Android 폰에서 접근 가능한지 확인:

```bash
cat .env.local
# EXPO_PUBLIC_API_BASE_URL=http://192.168.123.129:8080/api/v1
```

- 폰이 같은 Wi-Fi 인지 확인
- 폰 브라우저에서 `http://192.168.123.129:8080/actuator/health` 응답 OK 확인

## EAS Build 실행

```bash
cd front
eas build --profile development --platform android
```

- 처음 빌드 시: Android keystore 자동 생성 (Expo 가 관리)
- 빌드 시간: 약 10~15분 (Expo 서버 큐 대기 포함)
- 완료 시 https://expo.dev/accounts/.../builds/<id> 에서 APK 다운로드

## APK 설치 (Android 폰)

1. Expo 빌드 페이지의 QR 스캔 → APK 다운로드 링크 → 폰에서 다운로드
2. 폰 설정 → 보안 → "출처를 알 수 없는 앱 설치" 허용
3. APK 파일 열기 → 설치

## Dev Server 연결

폰에 PillMate dev client 가 설치된 후:

```bash
cd front
npx expo start --dev-client
```

- ⚠️ `--dev-client` 필수 (Expo Go 가 아닌 자체 dev build 로 연결)
- Terminal QR 코드를 폰의 PillMate dev client 앱에서 스캔
- 또는 `exp+pillmate://expo-development-client/?url=http%3A%2F%2F192.168.123.129%3A8081` URL 입력

## Push 알림 검증

dev client 는 expo-notifications 정상 동작 (Expo Go 미지원 제약 우회):

1. 앱 시작 시 NotificationsBootstrap 권한 다이얼로그 노출
2. 허용 → ExpoPushToken 발급
3. BE `POST /users/me/device-token` 호출 (docker logs 검증)
4. CTO 또는 사용자가 `https://exp.host/--/api/v2/push/send` 로 테스트 푸시 → 폰 수신 + tap → deep-link 동작

## 빌드 실패 시

- Expo 계정 무료 plan 빌드 큐 대기 시간 길 수 있음 (피크 시간 회피)
- `eas build:list` 로 진행 상태 조회
- 로그: `eas build:view <build-id>`

## Preview / Production (Phase 4 후속)

```bash
eas build --profile preview --platform android       # 내부 베타용 APK
eas build --profile production --platform android    # 실 배포 (AAB)
```
