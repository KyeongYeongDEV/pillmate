import React, { useCallback } from 'react';
import {
  View, Text, Pressable, ScrollView, StyleSheet, ImageBackground,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { router } from 'expo-router';
import * as ImagePicker from 'expo-image-picker';
import { Feather } from '@expo/vector-icons';
import { Image } from 'expo-image';
import { scale, colors, typography, space, radius, shadows } from '@/styles/tokens';
import { useAppDispatch } from '@/store/hooks';
import { reset, setPrescribedAt } from '@/store/slices/prescriptionFlowSlice';
import { getKstToday } from '@/utils/calendarUtils';
import { safeBack } from '@/lib/router/safeBack';

export default function PrescriptionRegisterHub() {
  const dispatch = useAppDispatch();

  const handleCamera = useCallback(() => {
    dispatch(reset());
    router.push('/prescription/camera' as any);
  }, [dispatch]);

  const handleGallery = useCallback(async () => {
    const { status } = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (status !== 'granted') return;
    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ['images'],
      quality: 0.9,
    });
    if (!result.canceled && result.assets[0]) {
      dispatch(reset());
      router.push({ pathname: '/prescription/scan' as any, params: { galleryUri: result.assets[0].uri } });
    }
  }, [dispatch]);

  const handleManual = useCallback(() => {
    dispatch(reset());
    dispatch(setPrescribedAt(getKstToday()));
    router.push('/prescription/review' as any);
  }, [dispatch]);

  const handleSearch = useCallback(() => {
    // mode 파라미터 미전달 = 순수 조회 전용(+ 버튼 없음). 추가 가능 모드는 review.tsx 의 "검색으로 추가"만.
    router.push('/prescription/search' as any);
  }, []);

  return (
    <SafeAreaView style={styles.root} edges={['top']}>
      {/* 헤더 */}
      <View style={styles.header}>
        <Pressable onPress={() => safeBack('/(tabs)/prescriptions')} accessibilityLabel="뒤로" accessibilityRole="button" style={styles.headerBtn}>
          <Text style={styles.headerBtnTxt}>←</Text>
        </Pressable>
        <Text style={styles.headerTitle}>내 약봉투 등록</Text>
        <View style={styles.headerBtn} />
      </View>

      <ScrollView contentContainerStyle={styles.scroll} showsVerticalScrollIndicator={false}>
        {/* 카메라 CTA (메인) */}
        <Pressable
          style={styles.cameraBtnShadow}
          onPress={handleCamera}
          accessibilityLabel="카메라로 촬영하기"
          accessibilityRole="button"
        >
          <ImageBackground
            source={require('@/assets/images/ai-gradient.png')}
            resizeMode="cover"
            style={styles.cameraBtn}
            imageStyle={styles.cameraBtnImg}
          >
            <View style={styles.cameraIconBox}>
              <Image
                source={require('@/assets/images/ai-sparkle.png')}
                style={styles.cameraSparkle}
                contentFit="contain"
              />
            </View>
            <View style={styles.cameraTxtBox}>
              <Text style={styles.cameraTitle}>카메라로 촬영하기</Text>
              <Text style={styles.cameraSub}>AI가 약을 자동으로 인식해 등록해드려요</Text>
            </View>
            <Text style={styles.chevron}>›</Text>
          </ImageBackground>
        </Pressable>

        {/* 보조 CTA 행 */}
        <View style={styles.secondaryRow}>
          <Pressable
            style={styles.secondaryBtn}
            onPress={handleGallery}
            accessibilityLabel="갤러리에서 선택"
            accessibilityRole="button"
          >
            <Text style={styles.secondaryIcon}>🖼</Text>
            <Text style={styles.secondaryTxt}>갤러리에서</Text>
          </Pressable>
          <Pressable
            style={styles.secondaryBtn}
            onPress={handleManual}
            accessibilityLabel="직접 입력"
            accessibilityRole="button"
          >
            <Text style={styles.secondaryIcon}>✏️</Text>
            <Text style={styles.secondaryTxt}>직접 입력</Text>
          </Pressable>
        </View>

        {/* 약 검색 CTA */}
        <Pressable
          style={styles.searchBtn}
          onPress={handleSearch}
          accessibilityLabel="약 검색하기"
          accessibilityRole="button"
        >
          <Feather name="search" size={scale(18)} color={colors.primaryBase} />
          <Text style={styles.searchBtnTxt}>약 검색하기</Text>
          <Feather name="chevron-right" size={scale(16)} color={colors.labelAlternative} style={styles.searchChevron} />
        </Pressable>

        {/* 촬영 팁 */}
        <View style={styles.tipCard}>
          <Text style={styles.tipTitle}>📌 촬영 팁</Text>
          {[
            '약봉투 전체가 보이도록 촬영하세요',
            '조명이 충분한 곳에서 촬영하세요',
            '글자가 흐릿하면 사용자 확인이 필요해요',
          ].map((tip) => (
            <Text key={tip} style={styles.tipItem}>· {tip}</Text>
          ))}
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.bgAlt },
  header: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    paddingHorizontal: space.s16, paddingVertical: space.s12,
    backgroundColor: colors.bgNormal, borderBottomWidth: 1, borderBottomColor: colors.line,
  },
  headerBtn: { width: scale(40), alignItems: 'center' },
  headerBtnTxt: { fontSize: scale(22), color: colors.labelNormal },
  headerTitle: { ...typography.headline1, color: colors.labelNormal },
  scroll: { padding: space.s16, gap: space.s12, paddingBottom: space.s48 },
  cameraBtnShadow: { borderRadius: radius.r16, ...shadows.medium },
  cameraBtn: {
    flexDirection: 'row', alignItems: 'center', gap: space.s16,
    borderRadius: radius.r16, padding: space.s20, overflow: 'hidden',
  },
  cameraBtnImg: { borderRadius: radius.r16 },
  cameraIconBox: { width: scale(56), height: scale(56), borderRadius: radius.r16, backgroundColor: 'rgba(255,255,255,0.14)', alignItems: 'center', justifyContent: 'center' },
  cameraSparkle: { width: scale(36), height: scale(36) },
  cameraTxtBox: { flex: 1 },
  cameraTitle: { ...typography.headline1, color: '#fff' },
  cameraSub: { ...typography.caption1, color: 'rgba(255,255,255,0.7)', marginTop: 2 },
  chevron: { fontSize: scale(24), color: 'rgba(255,255,255,0.6)' },
  secondaryRow: { flexDirection: 'row', gap: space.s10 },
  secondaryBtn: {
    flex: 1, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: space.s8,
    backgroundColor: colors.bgNormal, borderRadius: radius.r12, padding: space.s16,
    borderWidth: 1, borderColor: colors.line,
  },
  secondaryIcon: { fontSize: scale(20) },
  secondaryTxt: { ...typography.body2n, color: colors.labelNormal, fontWeight: '600' },
  tipCard: { backgroundColor: colors.bgAlt, borderRadius: radius.r12, padding: space.s16, gap: space.s8, borderWidth: 1, borderColor: colors.line },
  tipTitle: { ...typography.label1n, color: colors.labelNeutral, fontWeight: '700' },
  tipItem: { ...typography.body2r, color: colors.labelAlternative },
  searchBtn: {
    flexDirection: 'row', alignItems: 'center', gap: space.s10,
    backgroundColor: colors.bgNormal, borderRadius: radius.r12, padding: space.s16,
    borderWidth: 1.5, borderColor: colors.primaryBase, borderStyle: 'dashed',
  },
  searchBtnTxt: { ...typography.body2n, color: colors.primaryBase, fontWeight: '600', flex: 1 },
  searchChevron: { marginLeft: 'auto' },
});
