import React, { useCallback } from 'react';
import {
  View, Text, Pressable, ScrollView, StyleSheet,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { router } from 'expo-router';
import * as ImagePicker from 'expo-image-picker';
import { Feather } from '@expo/vector-icons';
import PillVisual from '@/components/common/PillVisual';
import { scale, colors, typography, space, radius, shadows } from '@/styles/tokens';
import { useAppDispatch, useAppSelector } from '@/store/hooks';
import { reset, removeItem } from '@/store/slices/prescriptionFlowSlice';
import { safeBack } from '@/lib/router/safeBack';

export default function PrescriptionRegisterHub() {
  const dispatch = useAppDispatch();
  const selectedItems = useAppSelector(s => s.prescriptionFlow.items);

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
    router.push('/prescription/manual' as any);
  }, [dispatch]);

  const handleSearch = useCallback(() => {
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
        {/* 안내 카드 */}
        <View style={styles.heroCard}>
          <View style={styles.prescriptionIcon}>
            <Text style={styles.prescriptionEmoji}>📋</Text>
            <View style={styles.aiBadge}><Text style={styles.aiBadgeTxt}>✨</Text></View>
          </View>
          <View style={styles.heroText}>
            <Text style={styles.heroTitle}>어떻게 등록할까요?</Text>
            <Text style={styles.heroSub}>AI가 1.4초 만에 약을 인식해{'\n'}자동으로 등록해드려요</Text>
          </View>
        </View>

        {/* 카메라 CTA (메인) */}
        <Pressable
          style={styles.cameraBtn}
          onPress={handleCamera}
          accessibilityLabel="카메라로 촬영하기"
          accessibilityRole="button"
        >
          <View style={styles.cameraIconBox}>
            <Text style={styles.cameraIcon}>📷</Text>
          </View>
          <View style={styles.cameraTxtBox}>
            <Text style={styles.cameraLabel}>추천 · 가장 빠름</Text>
            <Text style={styles.cameraTitle}>카메라로 촬영하기</Text>
            <Text style={styles.cameraSub}>약봉투를 사각형 안에 맞추세요</Text>
          </View>
          <Text style={styles.chevron}>›</Text>
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

        {/* 추가된 약 목록 */}
        {selectedItems.length > 0 && (
          <View style={styles.selectedSection}>
            <Text style={styles.selectedTitle}>추가된 약 · {selectedItems.length}개</Text>
            {selectedItems.map(item => (
              <View key={item.id} style={styles.selectedItem}>
                <PillVisual size={scale(32)} colorA="#a5c8f5" colorB="#d0e8ff" />
                <Text style={styles.selectedName} numberOfLines={1}>
                  {item.matchedName ?? item.nameRaw}
                </Text>
                <Pressable
                  onPress={() => dispatch(removeItem(item.id))}
                  accessibilityLabel={`${item.matchedName ?? item.nameRaw} 제거`}
                  accessibilityRole="button"
                  style={styles.removeItemBtn}
                >
                  <Feather name="x" size={scale(14)} color={colors.labelAlternative} />
                </Pressable>
              </View>
            ))}
          </View>
        )}

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
  heroCard: {
    flexDirection: 'row', alignItems: 'center', gap: space.s16,
    backgroundColor: colors.bgNormal, borderRadius: radius.r16, padding: space.s16,
    borderWidth: 1, borderColor: colors.line, ...shadows.small,
  },
  prescriptionIcon: { width: scale(60), height: scale(76), backgroundColor: '#F4F1EA', borderRadius: scale(6), alignItems: 'center', justifyContent: 'center', position: 'relative' },
  prescriptionEmoji: { fontSize: scale(28) },
  aiBadge: { position: 'absolute', top: -6, right: -6, width: scale(20), height: scale(20), borderRadius: scale(10), backgroundColor: colors.accentViolet, alignItems: 'center', justifyContent: 'center' },
  aiBadgeTxt: { fontSize: scale(10) },
  heroText: { flex: 1 },
  heroTitle: { ...typography.headline2, color: colors.labelNormal },
  heroSub: { ...typography.caption1, color: colors.labelAlternative, marginTop: 4, lineHeight: scale(18) },
  cameraBtn: {
    flexDirection: 'row', alignItems: 'center', gap: space.s16,
    backgroundColor: colors.labelNormal, borderRadius: radius.r16, padding: space.s20,
    ...shadows.medium,
  },
  cameraIconBox: { width: scale(56), height: scale(56), borderRadius: radius.r16, backgroundColor: 'rgba(255,255,255,0.14)', alignItems: 'center', justifyContent: 'center' },
  cameraIcon: { fontSize: scale(28) },
  cameraTxtBox: { flex: 1 },
  cameraLabel: { ...typography.caption1, color: 'rgba(255,255,255,0.7)', fontWeight: '700', textTransform: 'uppercase' },
  cameraTitle: { ...typography.headline1, color: '#fff', marginTop: 4 },
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
  selectedSection: {
    backgroundColor: colors.bgNormal, borderRadius: radius.r12,
    borderWidth: 1, borderColor: colors.line, overflow: 'hidden',
  },
  selectedTitle: {
    fontSize: scale(11), fontWeight: '700', color: colors.labelAlternative,
    letterSpacing: 0.06, paddingHorizontal: space.s14, paddingVertical: space.s10,
    borderBottomWidth: 1, borderBottomColor: colors.line,
  },
  selectedItem: {
    flexDirection: 'row', alignItems: 'center', gap: space.s10,
    paddingHorizontal: space.s14, paddingVertical: space.s10,
    borderBottomWidth: 1, borderBottomColor: colors.line,
  },
  selectedName: { ...typography.label1n, color: colors.labelNormal, flex: 1, fontWeight: '600' },
  removeItemBtn: { padding: space.s4 },
});
