# Spec — 온보딩 · 카카오 로그인 · 설정(MY) · BE 카카오 연동

> 결정 (2026-06-23, 사용자 확정)
> - 온보딩 3페이지(목업대로) / 로그인 **카카오만**(네이버·Google·이메일 제거) / 설정 = 약관·개인정보·앱버전·로그아웃 (**알림 토글 없음**)
> - BE: 카카오 실제 연동 **코드만** 구현. 앱키 등 실제 값은 추후. X-User-Id dev stub 공존.
> - #99(인증) 블로커 해소 작업. Phase 2 인프라와 별개.

---

## 공통 API 계약 (FE ↔ BE 동일)

**POST `/api/v1/auth/kakao`** (Authorization Code 플로우 — client secret은 BE에만)
- 요청: `{ "code": string, "redirectUri": string }`  (FE가 카카오 인가에서 받은 authorization code; 미구성 dev 모드면 빈 문자열)
- 응답: `ApiResponse<AuthResult>`
  ```json
  { "data": { "token": "<app JWT>", "userId": 12, "isNewUser": true,
              "profile": { "name": "홍길동", "email": "a@b.c", "profileUrl": "https://..." } },
    "message": "OK", "timestamp": "..." }
  ```
- BE 동작:
  - **앱키 구성 시**: code → Kakao `/oauth/token`(client-id/secret) → access token → Kakao `/v2/user/me` → kakaoId/email/nickname/profile → `User.ofOAuth(kakaoId, KAKAO, name, email)` upsert → app JWT 발급
  - **앱키 미구성(dev)**: code/secret 없음 → **기존 seed 사용자(userId=1)** 로 resolve → JWT 발급. 로그로 "DEV kakao fallback" WARN. (앱이 지금도 seed 데이터로 동작하도록; 새 dev 유저 만들지 말 것 — 기존 처방전/그룹 연결 보존)
- 인증: 이 엔드포인트는 인증 예외(토큰 없이 호출).

> 핵심: FE는 항상 이 한 엔드포인트만 호출("카카오만"). 실제/dev 분기는 BE가 담당 → 키 추가 시 FE 무변경.
> client secret은 절대 FE/깃에 두지 않음 — `KAKAO_CLIENT_SECRET` env (추후 주입).

---

## FE 작업 (FE-Dev)

### F1. 라우팅 게이트
- `src/app/(auth)/_layout.tsx` 신설 (Stack, headerShown:false)
- `src/app/(auth)/onboarding.tsx`, `src/app/(auth)/login.tsx` 신설
- `src/app/_layout.tsx`: 부팅 시 `getToken()` 확인 →
  - 토큰 없음 → `(auth)` (최초면 onboarding, 아니면 login)
  - 토큰 있음 → `(tabs)`
  - "온보딩 본 적 있음" 플래그: SecureStore 또는 AsyncStorage `onboarding_seen` 키. 최초 1회만 온보딩, 이후 바로 login.
- 스플래시 깜빡임 방지: 토큰/플래그 조회 끝나기 전엔 로딩(또는 splash 유지).

### F2. 온보딩 (`onboarding.tsx`) — 목업 01 그대로
- 상단: PillMate 로고(좌) + "건너뛰기"(우, → login)
- 비주얼: 기울어진 처방전 카드(박○○·만72세 + 약 4개 목록) + "AI · 1.4초 만에 인식 / 4개 약 자동 등록됨" 카드 레이어드 + 알약 dots
- 헤딩: "처방전 한 장으로\n온 가족 복약 관리" (typography.title1/title2)
- 본문: "사진만 찍으면 AI가 약을 인식해 자동 등록합니다.\n식약처 데이터로 검증된 안전한 복약 정보를 받아보세요."
- 점 인디케이터(3페이지) + 검정 "다음" 버튼(마지막 페이지는 "시작하기" → login)
- 하단: "이미 계정이 있으면 로그인" (→ login)
- 3페이지 콘텐츠: ①처방전 인식(위) ②가족 그룹 복약 관리 ③복약 알림/캘린더 (문구는 FE-Dev가 기존 톤으로, 의료 과장 금지)
- 완료/건너뛰기 시 `onboarding_seen=true` 저장

### F3. 로그인 (`login.tsx`) — 목업 02, **카카오만**
- 중앙: PillMate 로고 아이콘 + "PillMate" 워드마크 + "처방전 한 장으로\n온 가족의 복약을 함께 관리해요"
- "간편 로그인으로 시작하세요"
- **카카오 버튼 1개**: 노랑(#FEE500) 배경, "카카오로 계속하기", 좌측 말풍선 아이콘, "3초 만에 시작" 뱃지. (네이버/Google/이메일/divider 전부 제거)
- 하단: "가입 시 이용약관과 개인정보 처리방침에 동의한 것으로 간주됩니다." (약관/개인정보 링크)
- 동작:
  - `expo-auth-session` + `expo-web-browser`로 카카오 인가 화면 → authorization `code` + `redirectUri` 획득 시도
  - `{ code, redirectUri }`(미설정이면 빈 문자열) → `useKakaoLoginMutation` 호출
  - 성공: `saveToken(token)` + `setCurrentUserId(userId)` → `router.replace('/(tabs)/home')`
  - 실패: 토스트/배너 ("로그인에 실패했어요. 다시 시도해 주세요.")
- 카카오 client id는 `process.env.EXPO_PUBLIC_KAKAO_REST_API_KEY` (미설정이면 빈 code로 진행 → BE dev fallback = seed userId 1). **client secret은 FE에 두지 않음.**
- `expo-auth-session` 신규 설치 필요

### F4. 인증 상태/스토리지
- `src/lib/auth/storage.ts`: `setCurrentUserId(id)` / `clearCurrentUserId()` 추가, `getCurrentUserId()`는 저장값 우선 + 없으면 기존 dev 폴백(1) 유지(하위호환)
- `clearAuth()` 헬퍼: `clearToken()` + `clearCurrentUserId()`
- `client.ts`의 하드코딩 `X-User-Id:"1"` → `getCurrentUserId()` 사용으로 교체(baseQuery와 일관)

### F5. RTK 슬라이스
- `src/store/slices/authApi.ts` 신설(createApi, reducerPath:'authApi', baseQuery 공용)
  - `kakaoLogin` mutation: `POST /auth/kakao`, transformResponse `data`, onQueryStarted에서 saveToken+setCurrentUserId
- `src/store/index.ts`에 authApiSlice 등록

### F6. 설정(MY) (`(tabs)/my.tsx` 교체)
- 상단 프로필 헤더: 이름·이메일·프로필 이미지(로그인 응답 profile 또는 me 조회). 데이터 없으면 이름만/플레이스홀더.
- 리스트 항목(목업 톤, 카드/Row 형태):
  - 약관 (이용약관) → 링크
  - 개인정보 처리방침 → 링크
  - 앱 버전: `Constants.expoConfig?.version` 표시(우측 회색 "1.0.0")
  - **로그아웃**: 탭 → 확인 다이얼로그 → `clearAuth()` → `router.replace('/(auth)/login')`
- **알림 토글 없음** (사용자 명시)
- MY를 탭바에 넣지 않음 — 홈 우상단 기어(이미 `/(tabs)/my` 이동)에서 진입하는 현 구조 유지. 단 my를 (tabs) 밖 일반 스택으로 옮길지 여부는 FE-Dev 판단(현 기어 네비 깨지지 않게).

### FE 비고
- 디자인 토큰만 사용(`styles/tokens.ts`), 매직넘버 금지, 클린코드 룰 적용
- 약관/개인정보 URL은 상수(placeholder URL 가능, 추후 교체) — 의료/법무 텍스트 임의 생성 금지
- 의료 과장 카피 금지(medical-safety)

---

## BE 작업 (BE-Dev) — 확정 (조사 반영)

> 오버엔지니어링 회피: **Spring Security 도입하지 않음.** 기존 `UserContextInterceptor` 확장으로 JWT 처리.
> JJWT 0.12.6 이미 classpath. `User.ofOAuth(...)`·`UserProvider.KAKAO`·`external_id` unique 컬럼 존재 → **마이그레이션 없음.**

### B1. UserRepository 확장 (domain + infra)
- `domain/repository/UserRepository`: `Optional<User> findByProviderAndExternalId(UserProvider provider, String externalId)` 추가
- `infrastructure/.../UserJpaRepository`: 동명 메서드 / `UserRepositoryImpl` 위임

### B2. JwtTokenProvider (`common/security`)
- JJWT 0.12.6로 `issue(Long userId)` / `Long parseUserId(String jwt)` (만료·서명 검증)
- 시크릿/만료: `application.yml` — `pillmate.auth.jwt.secret=${PILLMATE_JWT_SECRET:dev-only-... }`(dev 기본값), `access-ttl` (예: 14d)
- 단위 테스트(발급→파싱 라운드트립, 만료/위조 거부)

### B3. KakaoOAuthClient (`user/infrastructure`) — RestClient, `AiServerOcrClient` 패턴 미러
- `KakaoOAuthConfig`: `kakao.token-url`(https://kauth.kakao.com/oauth/token), `kakao.user-info-url`(https://kapi.kakao.com/v2/user/me), `client-id=${KAKAO_REST_API_KEY:}`, `client-secret=${KAKAO_CLIENT_SECRET:}`
- `exchange(code, redirectUri) → KakaoProfile{ kakaoId, nickname, email, profileImageUrl }`
- **dev fallback**: client-id 비었거나 code 비었으면 호출 안 함 → UseCase에서 처리

### B4. KakaoLoginUseCase/Service (`user/application`)
- 입력 `{ code, redirectUri }`
- client-id 구성 && code 있음 → `KakaoOAuthClient.exchange` → `User.ofOAuth(kakaoId, KAKAO, name, email)` upsert(`findByProviderAndExternalId` 없으면 save) → JWT
- **dev fallback** (미구성) → `userRepository.findById(1)` (seed 사용자) 사용, 없으면 dummy 생성 → JWT. `log.warn("DEV kakao fallback → userId=1")`
- 반환 `AuthResult{ token, userId, isNewUser, profile }`
- service ≤20줄(clean-code) — 분기/매핑 private 추출

### B5. AuthController (`user/presentation`)
- `POST /auth/kakao` → `ResponseEntity<ApiResponse<AuthResultResponse>>`, `ApiResponse.success(...)`
- request record `KakaoLoginRequest(String code, String redirectUri)` (code blank 허용)
- **인증 예외 경로**: 이 경로는 토큰 불필요

### B6. UserContextInterceptor 확장 (`common/security`)
- preHandle: `Authorization: Bearer <jwt>` 있으면 `JwtTokenProvider.parseUserId` → `UserContext.set` (우선)
- 없으면 기존 `X-User-Id` 폴백 (dev 유지)
- JWT 위조/만료 → 401 (`ApiResponse.error`) — 단, 비인증 허용 경로(/auth/**, swagger 등)는 통과
- `/auth/**`는 인터셉터 excludePathPatterns

### B7. ErrorCode 추가 (`common/exception/ErrorCode`)
- `KAKAO_AUTH_FAILED("PILL_083", "카카오 인증에 실패했습니다.")` (401)
- `INVALID_AUTH_TOKEN("PILL_084", "인증 토큰이 유효하지 않습니다.")` (401)
- (다음 가용 번호 확인 후 사용)

### B8. TDD / 안전
- `KakaoLoginServiceTest`(신규가입/기존로그인/dev fallback=userId1), `JwtTokenProviderTest`, `AuthControllerTest`(@WebMvcTest), 인터셉터 JWT 우선/폴백 테스트
- DDD 레이어 준수(ArchUnit 통과), DB 삭제/변경 금지(db-safety), 환자정보 로깅 금지(medical-safety)
- 기존 V1~V31 마이그레이션 수정 금지
