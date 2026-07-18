import React, { useState, useEffect } from 'react';
import {
  View, Text, Pressable, StyleSheet, ActivityIndicator,
  Animated, Linking,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { router } from 'expo-router';
import * as Device from 'expo-device';
import { Image } from 'expo-image';
import { useKakaoLoginMutation, useKakaoNativeLoginMutation } from '@/store/slices/authApi';
import { registerPushForCurrentUser } from '@/lib/notifications/pushRegistration';
import KakaoTalkIcon from '@/components/common/KakaoTalkIcon';
import { colors, space, scale, radius, typography } from '@/styles/tokens';

const TERMS_URL = 'https://pillmate.app/terms';
const PRIVACY_URL = 'https://pillmate.app/privacy';
const TOAST_DURATION_MS = 3000;
// Kakao 브랜드 버튼 색상 — 임의 매직넘버가 아닌 Kakao 공식 스펙
const KAKAO_YELLOW = '#FEE500';
const KAKAO_TEXT = '#191600';

export default function LoginScreen() {
  const [kakaoLogin, { isLoading: kakaoLoading }] = useKakaoLoginMutation();
  const [kakaoNativeLogin, { isLoading: nativeLoading }] = useKakaoNativeLoginMutation();
  const isLoading = kakaoLoading || nativeLoading;
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const toastOpacity = useState(() => new Animated.Value(0))[0];
  // Pressable style function-form 은 New Arch 에서 무시되는 재현 케이스 확인됨 — 정적 스타일 + 로컬 state 로 pressed 처리
  const [kakaoPressed, setKakaoPressed] = useState(false);

  async function loginWithCode(code: string, uri: string) {
    try {
      await kakaoLogin({ code, redirectUri: uri }).unwrap();
      void registerPushForCurrentUser();
      router.replace('/(tabs)/home');
    } catch {
      showError('로그인에 실패했어요. 다시 시도해 주세요.');
    }
  }

  // 시뮬레이터/에뮬레이터: 로그인 화면 진입 즉시 dev-fallback 자동로그인(임의 userId)
  useEffect(() => {
    if (!Device.isDevice) {
      loginWithCode('', '');
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function showError(msg: string) {
    setErrorMsg(msg);
    Animated.sequence([
      Animated.timing(toastOpacity, { toValue: 1, duration: 180, useNativeDriver: true }),
      Animated.delay(TOAST_DURATION_MS - 360),
      Animated.timing(toastOpacity, { toValue: 0, duration: 180, useNativeDriver: true }),
    ]).start(() => setErrorMsg(null));
  }

  async function handleKakaoPress() {
    if (!Device.isDevice) {
      // dev-fallback: 빈 code → BE seed userId 반환 (시뮬레이터는 네이티브 SDK 미지원)
      await loginWithCode('', '');
      return;
    }

    try {
      // 동적 require: 시뮬레이터 분기에선 네이티브 모듈을 평가하지 않는다.
      // eslint-disable-next-line @typescript-eslint/no-var-requires
      const { login } = require('@react-native-seoul/kakao-login') as typeof import('@react-native-seoul/kakao-login');
      // 카톡 미설치 시 SDK 가 카카오계정 웹 로그인으로 자동 폴백.
      const { accessToken } = await login();
      if (!accessToken) {
        showError('로그인 정보를 받아오지 못했어요. 다시 시도해 주세요.');
        return;
      }
      await kakaoNativeLogin({ accessToken }).unwrap();
      void registerPushForCurrentUser();
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
            <Image
              source={require('@/assets/images/icon.png')}
              style={styles.logoImage}
              contentFit="contain"
            />
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
            {/* 외곽 View가 높이/배경/모서리 담당 — Pressable 은 stretch 만(가설2), style 은 정적 배열(가설1) */}
            <View style={styles.kakaoBtnBg}>
              <Pressable
                style={styles.kakaoBtnInner}
                onPress={handleKakaoPress}
                onPressIn={() => setKakaoPressed(true)}
                onPressOut={() => setKakaoPressed(false)}
                disabled={isLoading}
                accessibilityLabel="카카오로 시작"
                accessibilityRole="button"
              >
                <View style={[styles.kakaoBtnContent, kakaoPressed && styles.kakaoBtnPressed]}>
                  {isLoading ? (
                    <ActivityIndicator size="small" color={KAKAO_TEXT} />
                  ) : (
                    <>
                      <KakaoTalkIcon size={scale(20)} color={KAKAO_TEXT} />
                      <Text style={styles.kakaoBtnTxt}>카카오로 시작</Text>
                    </>
                  )}
                </View>
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
  logoImage: { width: scale(48), height: scale(48) },
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
    height: scale(45), // 높이는 outer View 에 명시 — Pressable 내부 minHeight 무시 재발 방지. 사용자 피드백(88→60→45, 단계적 축소)
    justifyContent: 'center',
  },
  kakaoBtnInner: {
    flex: 1, alignSelf: 'stretch', width: '100%',
    alignItems: 'center', justifyContent: 'center',
  },
  kakaoBtnContent: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: space.s10,
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
