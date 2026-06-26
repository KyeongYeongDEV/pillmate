# Spec — 카카오 로그인 https 콜백 + 딥링크 바운스 (배포 대비)

> 문제: 카카오는 redirect URI로 커스텀스킴(`pillmate://`) 거부, https만 허용. 배포 도메인 필요.
> 해법: 카카오 redirect = BE의 https 콜백 → BE가 코드 교환 후 앱 딥링크로 결과 바운스.
> 로컬 dev: 기존 dev-fallback(빈 code→userId1) 그대로 — 카카오 미구성 시 OFF.

## 흐름 (프로덕션)
```
앱 → 카카오 authorize (redirect_uri = https://도메인/api/v1/auth/kakao/callback)
   → 카카오 → BE GET /auth/kakao/callback?code=...
   → BE: KakaoLoginService.login(code, 동일 redirect_uri) → AuthResult(token,userId,..)
   → BE 302 → {앱스킴}://oauth/kakao?token=<jwt>&userId=<id>&isNewUser=<bool>
   → 앱(WebBrowser.openAuthSessionAsync returnUrl=pillmate://oauth/kakao)가 딥링크 잡아 token 추출
   → saveToken + setCurrentUserId → 홈
```

## BE (T-BE-KAKAO-CALLBACK)
- `GET /api/v1/auth/kakao/callback` (★인증 예외 경로, /auth/** exclude):
  - `error` 파라미터 있으면 → 302 `${app.deeplink}?error=<error>`
  - `code` → KakaoLoginService.login(code, ${kakao.redirect-uri}) → AuthResult → 302 `${app.deeplink}?token=<jwt>&userId=<id>&isNewUser=<bool>`
  - ★token exchange의 redirect_uri는 authorize 때와 동일해야 함 → KakaoOAuthClient.exchange가 `kakao.redirect-uri`(= https 콜백) 사용하도록 확인/정합.
- application.yml: `kakao.redirect-uri=${KAKAO_REDIRECT_URI:}` (배포 때 https 콜백), `app.deeplink=${APP_DEEPLINK:pillmate://oauth/kakao}`.
- 기존 `POST /auth/kakao {code}` 유지(로컬 dev-fallback 경로).
- ★보안 메모(후속): 딥링크에 JWT 직접 노출 → MVP 허용(앱-투-앱), 추후 one-time code 교환으로 하드닝 가능. 로그에 token 출력 금지.
- TDD: 콜백이 code→AuthResult→302 딥링크(token 포함), error→302 error. 인증예외 경로.

## FE (T-FE-KAKAO-FLOW)
- login.tsx 분기:
  - `EXPO_PUBLIC_KAKAO_REST_API_KEY` 설정됨(프로덕션): WebBrowser.openAuthSessionAsync(
      authorizeUrl(redirect_uri=`EXPO_PUBLIC_KAKAO_REDIRECT_URI`= https 콜백, client_id=REST키, response_type=code),
      returnUrl=`pillmate://oauth/kakao`) → 결과 url 파라미터 token/userId 추출 → saveToken+setCurrentUserId → /(tabs)/home. error면 토스트.
  - 미설정(dev): 기존 그대로 → POST /auth/kakao {code:''} → dev-fallback → 홈.
- env: `EXPO_PUBLIC_KAKAO_REDIRECT_URI=https://도메인/api/v1/auth/kakao/callback` (배포 때). 미설정이면 dev 경로.
- 딥링크 수신은 openAuthSessionAsync 반환으로 처리(별도 expo-linking 리스너 불필요).
- tsc/jest 통과.

## 배포 시 사용자 액션 (이 코드와 별개)
- 도메인 확보(무료 DuckDNS 가능, 사업자 불필요) → 오라클 https 배포
- 카카오 콘솔 redirect URI = `https://도메인/api/v1/auth/kakao/callback` 등록
- .env.prod: KAKAO_REST_API_KEY, KAKAO_CLIENT_SECRET, KAKAO_REDIRECT_URI / FE EXPO_PUBLIC_*
- 테스터 카카오계정 팀원 등록(검수 전) → 실기기 e2e

## 안전
- 인증예외 경로만 콜백, db-safety/medical-safety 준수, token 로깅 금지. ★커밋 금지.
