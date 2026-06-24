import React, { useEffect, useState } from 'react';
import {
  View, Text, Pressable, StyleSheet, ActivityIndicator,
  Animated, SafeAreaView, Linking,
} from 'react-native';
import { router } from 'expo-router';
// type-only import: JS 생성 없음 → 모듈 평가 안 됨 → ExpoCrypto 미호출
import type * as AuthSessionTypes from 'expo-auth-session';
import { useKakaoLoginMutation } from '@/store/slices/authApi';
import { colors, space, scale, radius, typography } from '@/styles/tokens';

const KAKAO_REST_API_KEY = process.env.EXPO_PUBLIC_KAKAO_REST_API_KEY ?? '';
// 키 미설정(dev)에서는 OAuth/PKCE/crypto 경로에 절대 진입하지 않음
const HAS_KAKAO_KEY = Boolean(KAKAO_REST_API_KEY);
const KAKAO_AUTH_URL = 'https://kauth.kakao.com/oauth/authorize';
const TERMS_URL = 'https://pillmate.app/terms';
const PRIVACY_URL = 'https://pillmate.app/privacy';
const TOAST_DURATION_MS = 3000;
// Kakao 브랜드 버튼 색상 — 임의 매직넘버가 아닌 Kakao 공식 스펙
const KAKAO_YELLOW = '#FEE500';
const KAKAO_TEXT = '#191600';

export default function LoginScreen() {
  const [kakaoLogin, { isLoading }] = useKakaoLoginMutation();
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const toastOpacity = useState(() => new Animated.Value(0))[0];

  useEffect(() => {
    // OAuth 세션 복귀 처리 — 키 있을 때만 동적 require (네이티브 모듈 부하 방지)
    if (HAS_KAKAO_KEY) {
      // eslint-disable-next-line @typescript-eslint/no-var-requires
      require('expo-web-browser').maybeCompleteAuthSession();
    }
  }, []);

  async function loginWithCode(code: string, uri: string) {
    try {
      await kakaoLogin({ code, redirectUri: uri }).unwrap();
      router.replace('/(tabs)/home');
    } catch {
      showError('로그인에 실패했어요. 다시 시도해 주세요.');
    }
  }

  function showError(msg: string) {
    setErrorMsg(msg);
    Animated.sequence([
      Animated.timing(toastOpacity, { toValue: 1, duration: 180, useNativeDriver: true }),
      Animated.delay(TOAST_DURATION_MS - 360),
      Animated.timing(toastOpacity, { toValue: 0, duration: 180, useNativeDriver: true }),
    ]).start(() => setErrorMsg(null));
  }

  async function handleKakaoPress() {
    if (!HAS_KAKAO_KEY) {
      // 키 미설정 → crypto/PKCE 완전 비실행 → BE dev fallback(seed userId=1)
      await loginWithCode('', '');
      return;
    }

    // 키 있을 때만 OAuth 실행 (dev-client 재빌드 후 autolinking 완료 전제)
    try {
      // 동적 require: HAS_KAKAO_KEY false 분기에선 절대 평가 안 됨 → ExpoCrypto 미로드
      const AuthSession = require('expo-auth-session') as typeof AuthSessionTypes;
      const redirectUri = AuthSession.makeRedirectUri();
      const request = new AuthSession.AuthRequest({
        clientId: KAKAO_REST_API_KEY,
        responseType: AuthSession.ResponseType.Code,
        scopes: ['profile_nickname', 'account_email'],
        redirectUri,
      });
      const result = await request.promptAsync({ authorizationEndpoint: KAKAO_AUTH_URL });
      if (result.type === 'success') {
        await loginWithCode(result.params.code ?? '', redirectUri);
      }
    } catch {
      showError('로그인에 실패했어요. 다시 시도해 주세요.');
    }
  }

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.content}>
        {/* Logo area */}
        <View style={styles.logoArea}>
          <View style={styles.logoIcon}>
            <Text style={styles.logoEmoji}>💊</Text>
          </View>
          <Text style={styles.wordmark}>PillMate</Text>
          <Text style={styles.tagline}>
            처방전 한 장으로{'\n'}온 가족의 복약을 함께 관리해요
          </Text>
        </View>

        {/* Login section */}
        <View style={styles.loginArea}>
          <Text style={styles.loginPrompt}>간편 로그인으로 시작하세요</Text>

          <View style={styles.kakaoWrap}>
            <View style={styles.badge}>
              <Text style={styles.badgeTxt}>3초 만에 시작</Text>
            </View>
            <Pressable
              style={({ pressed }) => [styles.kakaoBtn, pressed && styles.kakaoBtnPressed]}
              onPress={handleKakaoPress}
              disabled={isLoading}
              accessibilityLabel="카카오로 계속하기"
              accessibilityRole="button"
            >
              {isLoading ? (
                <ActivityIndicator size="small" color={KAKAO_TEXT} />
              ) : (
                <>
                  <View style={styles.kakaoIcon}>
                    <Text style={styles.kakaoIconTxt}>💬</Text>
                  </View>
                  <Text style={styles.kakaoBtnTxt}>카카오로 계속하기</Text>
                </>
              )}
            </Pressable>
          </View>
        </View>

        {/* Terms */}
        <View style={styles.termsArea}>
          <Text style={styles.termsText}>
            가입 시{' '}
            <Text style={styles.termsLink} onPress={() => Linking.openURL(TERMS_URL)}>
              이용약관
            </Text>
            {' '}과{' '}
            <Text style={styles.termsLink} onPress={() => Linking.openURL(PRIVACY_URL)}>
              개인정보 처리방침
            </Text>
            {'\n'}에 동의한 것으로 간주됩니다.
          </Text>
        </View>
      </View>

      {/* Error toast */}
      {errorMsg && (
        <Animated.View style={[styles.toast, { opacity: toastOpacity }]} pointerEvents="none">
          <Text style={styles.toastTxt}>{errorMsg}</Text>
        </Animated.View>
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bgNormal },
  content: {
    flex: 1, paddingHorizontal: space.s28,
    justifyContent: 'space-between', paddingBottom: space.s32,
  },

  logoArea: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: space.s12 },
  logoIcon: {
    width: scale(72), height: scale(72), borderRadius: radius.r20,
    backgroundColor: colors.blue95, alignItems: 'center', justifyContent: 'center',
    marginBottom: space.s4,
  },
  logoEmoji: { fontSize: scale(36) },
  wordmark: { fontSize: scale(28), fontWeight: '800', color: colors.labelNormal, letterSpacing: -0.5 },
  tagline: {
    ...typography.body1r, color: colors.labelAlternative,
    textAlign: 'center', lineHeight: scale(26),
  },

  loginArea: { gap: space.s16 },
  loginPrompt: { ...typography.label1n, color: colors.labelAlternative, textAlign: 'center' },
  kakaoWrap: { alignItems: 'center', gap: space.s8 },
  badge: {
    paddingHorizontal: space.s10, paddingVertical: space.s4,
    backgroundColor: colors.fillNormal, borderRadius: radius.full,
  },
  badgeTxt: { fontSize: scale(12), fontWeight: '600', color: colors.labelAlternative },
  kakaoBtn: {
    width: '100%', flexDirection: 'row', alignItems: 'center', justifyContent: 'center',
    backgroundColor: KAKAO_YELLOW, borderRadius: radius.r14,
    paddingVertical: space.s16, gap: space.s10,
  },
  kakaoBtnPressed: { opacity: 0.85 },
  kakaoIcon: { width: scale(24), height: scale(24), alignItems: 'center', justifyContent: 'center' },
  kakaoIconTxt: { fontSize: scale(18) },
  kakaoBtnTxt: { fontSize: scale(16), fontWeight: '700', color: KAKAO_TEXT },

  termsArea: { alignItems: 'center' },
  termsText: { fontSize: scale(12), color: colors.labelAssistive, textAlign: 'center', lineHeight: scale(18) },
  termsLink: { color: colors.labelAlternative, textDecorationLine: 'underline' },

  toast: {
    position: 'absolute', bottom: space.s32, alignSelf: 'center',
    backgroundColor: 'rgba(23,23,25,0.88)', borderRadius: radius.r20,
    paddingHorizontal: space.s20, paddingVertical: space.s12, maxWidth: '85%',
  },
  toastTxt: { ...typography.label2, color: colors.bgNormal, fontWeight: '600', textAlign: 'center' },
});
