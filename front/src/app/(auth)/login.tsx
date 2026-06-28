import React, { useState } from 'react';
import {
  View, Text, Pressable, StyleSheet, ActivityIndicator,
  Animated, Linking,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { router } from 'expo-router';
import { useKakaoLoginMutation, useExchangeKakaoCodeMutation } from '@/store/slices/authApi';
import KakaoTalkIcon from '@/components/common/KakaoTalkIcon';
import { colors, space, scale, radius, typography } from '@/styles/tokens';

const KAKAO_REST_API_KEY = process.env.EXPO_PUBLIC_KAKAO_REST_API_KEY ?? '';
const KAKAO_REDIRECT_URI = process.env.EXPO_PUBLIC_KAKAO_REDIRECT_URI  ?? '';
// 프로덕션: REST 키 + https 콜백 URI 모두 설정된 경우. 미설정 → dev-fallback.
const IS_PROD_KAKAO  = Boolean(KAKAO_REST_API_KEY && KAKAO_REDIRECT_URI);
const KAKAO_AUTH_URL = 'https://kauth.kakao.com/oauth/authorize';
const RETURN_URL     = 'pillmate://oauth/kakao';
const TERMS_URL = 'https://pillmate.app/terms';
const PRIVACY_URL = 'https://pillmate.app/privacy';
const TOAST_DURATION_MS = 3000;
// Kakao 브랜드 버튼 색상 — 임의 매직넘버가 아닌 Kakao 공식 스펙
const KAKAO_YELLOW = '#FEE500';
const KAKAO_TEXT = '#191600';

export default function LoginScreen() {
  const [kakaoLogin, { isLoading: kakaoLoading }] = useKakaoLoginMutation();
  const [exchangeKakaoCode, { isLoading: exchangeLoading }] = useExchangeKakaoCodeMutation();
  const isLoading = kakaoLoading || exchangeLoading;
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const toastOpacity = useState(() => new Animated.Value(0))[0];

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
    if (!IS_PROD_KAKAO) {
      // dev-fallback: 빈 code → BE seed userId 반환
      await loginWithCode('', '');
      return;
    }

    try {
      // 동적 require: IS_PROD_KAKAO false 분기에선 절대 평가 안 됨
      // eslint-disable-next-line @typescript-eslint/no-var-requires
      const { openAuthSessionAsync } = require('expo-web-browser') as typeof import('expo-web-browser');
      const authorizeUrl =
        `${KAKAO_AUTH_URL}?client_id=${KAKAO_REST_API_KEY}` +
        `&redirect_uri=${encodeURIComponent(KAKAO_REDIRECT_URI)}` +
        `&response_type=code`;

      const result = await openAuthSessionAsync(authorizeUrl, RETURN_URL);
      if (result.type !== 'success') return;

      const qs = result.url.split('?')[1] ?? '';
      const params = new URLSearchParams(qs);

      if (params.get('error')) {
        showError('카카오 로그인을 취소했거나 오류가 발생했어요.');
        return;
      }

      const loginCode = params.get('loginCode') ?? '';
      if (!loginCode) {
        showError('로그인 정보를 받아오지 못했어요. 다시 시도해 주세요.');
        return;
      }
      await exchangeKakaoCode({ loginCode }).unwrap();
      router.replace('/(tabs)/home');
    } catch {
      showError('로그인에 실패했어요. 다시 시도해 주세요.');
    }
  }

  return (
    <SafeAreaView style={styles.safe} edges={['top', 'bottom']}>
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
            <View style={styles.badgeWrap}>
              <View style={styles.badge}>
                <Text style={styles.badgeTxt}>5초만에 빠른 회원가입</Text>
              </View>
              <View style={styles.badgeTail} />
            </View>
            {/* 외곽 View가 배경/모서리 담당 — Pressable 자체 배경 미렌더 회피 */}
            <View style={styles.kakaoBtnBg}>
              <Pressable
                style={({ pressed }) => [styles.kakaoBtnInner, pressed && styles.kakaoBtnPressed]}
                onPress={handleKakaoPress}
                disabled={isLoading}
                accessibilityLabel="카카오로 시작"
                accessibilityRole="button"
              >
                {isLoading ? (
                  <ActivityIndicator size="small" color={KAKAO_TEXT} />
                ) : (
                  <View style={{
                    flexDirection: 'row',
                    alignItems: 'center',
                    justifyContent: 'center',
                    gap: space.s10,
                  }}>
                    <KakaoTalkIcon size={scale(20)} color={KAKAO_TEXT} />
                    <Text style={styles.kakaoBtnTxt}>카카오로 시작</Text>
                  </View>
                )}
              </Pressable>
            </View>
          </View>

          <View style={styles.divider}>
            <View style={styles.dividerLine} />
            <Text style={styles.dividerTxt}>또는</Text>
            <View style={styles.dividerLine} />
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
  kakaoWrap: { alignItems: 'stretch', gap: space.s8, alignSelf: 'stretch' },
  badgeWrap: { alignItems: 'center', alignSelf: 'center' },
  badge: {
    paddingHorizontal: space.s12, paddingVertical: space.s4,
    backgroundColor: colors.statusNegative, borderRadius: radius.full,
  },
  badgeTxt: { fontSize: scale(12), fontWeight: '700', color: colors.staticWhite },
  badgeTail: {
    width: 0, height: 0, marginTop: -1,
    borderLeftWidth: scale(5), borderRightWidth: scale(5), borderTopWidth: scale(6),
    borderLeftColor: 'transparent', borderRightColor: 'transparent',
    borderTopColor: colors.statusNegative,
  },
  divider: { flexDirection: 'row', alignItems: 'center' },
  dividerLine: { flex: 1, height: 1, backgroundColor: colors.line },
  dividerTxt: { marginHorizontal: space.s12, fontSize: scale(12), color: colors.labelAssistive },
  kakaoBtnBg: {
    backgroundColor: KAKAO_YELLOW, borderRadius: radius.r14,
    overflow: 'hidden', // ripple 영역 clip + 둥근 모서리
  },
  kakaoBtnInner: {
    width: '100%', minHeight: scale(56),
    paddingVertical: space.s16,
    alignItems: 'center', justifyContent: 'center',
  },
  kakaoBtnPressed: { opacity: 0.85 },
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
