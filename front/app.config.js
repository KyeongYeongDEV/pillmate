// app.json 을 정적 베이스로 두고, env 기반 값(카카오 네이티브 앱 키)만 동적으로 주입한다.
// 네이티브 앱 키는 REST 키(EXPO_PUBLIC_KAKAO_REST_API_KEY)와 다른 값 — 콘솔 '네이티브 앱 키'.
const KAKAO_NATIVE_APP_KEY = process.env.EXPO_PUBLIC_KAKAO_NATIVE_APP_KEY ?? '';
const KAKAO_MAVEN_REPO = 'https://devrepo.kakao.com/nexus/content/groups/public/';

module.exports = ({ config }) => ({
  ...config,
  plugins: [
    ...(config.plugins ?? []),
    // 카카오 플러그인이 자체적으로 android.kotlinVersion + kotlin-gradle-plugin classpath 를 기록한다.
    // prop 미지정 시 기본 1.5.10 으로 떨어져 expo-build-properties 값을 덮어씀 → RN0.81 KSP 미지원 빌드 실패.
    // RN0.81 기본이자 KSP 지원 버전인 2.1.20 을 명시해 정합을 맞춘다.
    ['@react-native-seoul/kakao-login', { kakaoAppKey: KAKAO_NATIVE_APP_KEY, kotlinVersion: '2.1.20' }],
    ['expo-build-properties', { android: { extraMavenRepos: [KAKAO_MAVEN_REPO], kotlinVersion: '2.1.20' } }],
  ],
});
