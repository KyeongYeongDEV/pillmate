# SDK 54 다운그레이드 검증 가이드

> 2026-06-03 — Expo SDK 56 → 54 다운그레이드 (T-FE-SDK-DOWNGRADE) 검증 절차.

## 배경

iOS 실 기기 Expo Go (App Store) 는 SDK 56 미지원 (App Store 의 Expo Go 는 SDK 54 까지만 호환).
Apple Dev 미가입 → 별도 dev build 불가 → SDK 다운그레이드 채택.

## 사용자 실 기기 (iPhone) 검증 절차

```bash
cd front
npm install          # SDK 54 deps 동기화
npx expo start       # Metro 시작 (포트 8081)
```

Terminal 에 표시되는 QR 코드를 iPhone Expo Go 앱으로 스캔 → 앱 로드.

검증 화면 (5):
1. **홈** — `/(tabs)/home` — 오늘 복약 + AI 인사이트
2. **복약** — `/(tabs)/schedule` — 스케줄 + 슬롯
3. **그룹** — `/(tabs)/group` — 그룹 목록 + FAB
4. **처방전** — `/prescription` — 등록 허브
5. **약 검색** — `/prescription/search` — 검색 + 카테고리

## iOS 시뮬레이터 검증 (선택)

iOS 시뮬레이터 Expo Go 가 56 이면 SDK 54 앱 로드 불가. 시뮬레이터에서 검증하려면:

```bash
# 시뮬레이터의 Expo Go 56 제거
xcrun simctl uninstall booted host.exp.Exponent

# Metro 시작 — Expo CLI 가 SDK 54 호환 Expo Go 자동 설치
npx expo start --ios
# 프롬프트 "Install the recommended Expo Go version?" → Y
```

## 회귀 검증

```bash
cd front
npm test             # 305 PASS / 1 fail (ActivityItemFull pre-existing)
npx tsc --noEmit     # 0 errors
```

## 호환성 변경 (FE 코드 측)

- `CustomTabBar.tsx`: `BottomTabBarProps` import 경로
  - SDK 56: `expo-router/build/react-navigation/bottom-tabs`
  - SDK 54: `@react-navigation/bottom-tabs` (표준 경로)

## Rollback (필요 시)

```bash
cd front
cp package.json.bak56 package.json
cp package-lock.json.bak56 package-lock.json
rm -rf node_modules
npm install
```
