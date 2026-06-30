# PillMate Client (Expo + React Native)

PillMate 의 크로스플랫폼 모바일 앱. iOS + Android 동시 지원.

## 스택

- Expo SDK 56 (Managed) + React Native 0.85 + TypeScript strict
- expo-router (file-based, `src/app/`)
- NativeWind v4 (Tailwind on RN)
- TanStack Query (서버 상태) / Zustand (옵션)
- expo-secure-store (JWT, 환자 PII 평문 X)

## 부트스트랩

```bash
cd client
npm install --legacy-peer-deps
```

## 실행

```bash
npm run ios       # iOS 시뮬레이터
npm run android   # Android 에뮬레이터
npm start         # Metro + QR (시뮬/에뮬: Platform.OS 분기 자동)
npm run start:auto-ip  # 실기기: Mac LAN IP 자동 감지 → .env.local 갱신 + Metro 시작
```

## 테스트

```bash
npm run typecheck         # tsc --noEmit
npm test                  # jest 단위 (Button 등)
npm run test:e2e          # maestro test tests/e2e/
```

## 환경 변수

| 변수                       | 기본                                                                       |
| -------------------------- | -------------------------------------------------------------------------- |
| `EXPO_PUBLIC_API_BASE_URL` | iOS: `http://localhost:8080/api/v1` · Android: `http://10.0.2.2:8080/api/v1` |

Android 에뮬레이터는 호스트 머신의 `localhost` 에 `10.0.2.2` 로 접근한다. `src/lib/api/client.ts` 가 플랫폼별 자동 분기.

## 디렉터리

```
client/
├── src/
│   ├── app/                 # expo-router (Expo SDK56 default: src/app)
│   │   ├── (tabs)/          # 메인 탭 (home, prescriptions, group)
│   │   ├── _layout.tsx
│   │   └── +not-found.tsx
│   ├── components/ui/       # 디자인 시스템 (Button, Card, Heading, Text)
│   ├── lib/
│   │   ├── api/client.ts    # fetch 래퍼 + 인증 헤더
│   │   ├── auth/storage.ts  # SecureStore JWT
│   │   ├── constants.ts
│   │   └── theme.ts
│   └── global.css           # Tailwind directives
├── tests/
│   ├── unit/                # jest-expo + @testing-library/react-native
│   ├── e2e/                 # Maestro YAML
│   └── screenshots/         # 검증 캡처 (커밋 X)
├── tailwind.config.js
├── metro.config.js
└── babel.config.js
```

## 의료 안전 UX (룰)

- 모든 약 정보 화면에 `출처: 식품의약품안전처` 명시
- OCR 신뢰도 < 0.7 → 사용자 확인 UX 강제 (`src/lib/constants.ts` `OCR_MIN_CONFIDENCE`)
- JWT 는 `expo-secure-store` 만 사용
