// app.json 을 정적 베이스로 두고, env 기반 값(카카오 네이티브 앱 키)만 동적으로 주입한다.
// 네이티브 앱 키는 REST 키(EXPO_PUBLIC_KAKAO_REST_API_KEY)와 다른 값 — 콘솔 '네이티브 앱 키'.
const KAKAO_NATIVE_APP_KEY = process.env.EXPO_PUBLIC_KAKAO_NATIVE_APP_KEY ?? '';
const KAKAO_MAVEN_REPO = 'https://devrepo.kakao.com/nexus/content/groups/public/';

module.exports = ({ config }) => ({
  ...config,
  plugins: [
    ...(config.plugins ?? []),
    ['@react-native-seoul/kakao-login', { kakaoAppKey: KAKAO_NATIVE_APP_KEY }],
    ['expo-build-properties', { android: { extraMavenRepos: [KAKAO_MAVEN_REPO], kotlinVersion: '1.9.0' } }],
  ],
});
