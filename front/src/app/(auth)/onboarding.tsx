import React, { useState } from 'react';
import {
  View, Text, Pressable, StyleSheet,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { router } from 'expo-router';
import * as SecureStore from 'expo-secure-store';
import { ONBOARDING_SEEN_KEY } from '@/lib/auth/storage';
import { colors, space, scale, radius, typography } from '@/styles/tokens';

interface PageData {
  visual: React.ReactNode;
  heading: string;
  body: string;
  evidence?: { stats: string; source: string };
}

const PAGES: PageData[] = [
  {
    visual: <PrescriptionVisual />,
    heading: '처방전 한 장으로\n온 가족 복약 관리',
    body: '사진만 찍으면 약을 자동으로 인식해 등록합니다.\n식약처 데이터로 검증된 복약 정보를 받아보세요.',
    evidence: {
      stats:
        '국내 10개 이상 약을 복용하는 분이 95만 명을 넘어요.\n' +
        '여러 약을 함께 드시면 중복·약물 상호작용 위험이 커져요.\n' +
        'PillMate가 중복·상호작용을 한눈에 확인할 수 있도록 도와드려요.',
      source: '출처: 국민건강보험공단(2018), 질병관리청 국가건강정보포털',
    },
  },
  {
    visual: <GroupVisual />,
    heading: '가족과 함께\n복약 일정을 공유해요',
    body: '보호자와 환자가 함께 복약 현황을 확인하고\n알림을 받을 수 있어요.',
  },
  {
    visual: <AlarmVisual />,
    heading: '복약 시간을\n놓치지 마세요',
    body: '아침·점심·저녁 복약 알림을 받고\n복약 기록을 캘린더로 관리해요.',
  },
];

export default function OnboardingScreen() {
  const [page, setPage] = useState(0);
  const isLast = page === PAGES.length - 1;

  async function markSeenAndGo() {
    await SecureStore.setItemAsync(ONBOARDING_SEEN_KEY, 'true');
    router.replace('/(auth)/login');
  }

  function handleNext() {
    if (isLast) {
      markSeenAndGo();
    } else {
      setPage(p => p + 1);
    }
  }

  const current = PAGES[page];

  return (
    <SafeAreaView style={styles.safe} edges={['top', 'bottom']}>
      {/* Header */}
      <View style={styles.header}>
        <Text style={styles.logo}>💊 PillMate</Text>
        <Pressable
          onPress={markSeenAndGo}
          accessibilityLabel="온보딩 건너뛰기"
          accessibilityRole="button"
          hitSlop={8}
        >
          <Text style={styles.skip}>건너뛰기</Text>
        </Pressable>
      </View>

      {/* Visual area */}
      <View style={styles.visualArea}>{current.visual}</View>

      {/* Text */}
      <View style={styles.textArea}>
        <Text style={styles.heading}>{current.heading}</Text>
        <Text style={styles.body}>{current.body}</Text>
        {current.evidence && (
          <View style={styles.evidenceWrap}>
            <Text style={styles.evidenceStats}>{current.evidence.stats}</Text>
            <Text style={styles.evidenceSrc}>{current.evidence.source}</Text>
          </View>
        )}
      </View>

      {/* Dots */}
      <View style={styles.dots}>
        {PAGES.map((_, i) => (
          <View key={i} style={[styles.dot, i === page && styles.dotActive]} />
        ))}
      </View>

      {/* CTA */}
      <Pressable
        style={styles.cta}
        onPress={handleNext}
        accessibilityRole="button"
        accessibilityLabel={isLast ? '시작하기' : '다음'}
      >
        <Text style={styles.ctaTxt}>{isLast ? '시작하기' : '다음'}</Text>
      </Pressable>

      {/* Already have account */}
      <Pressable
        onPress={markSeenAndGo}
        accessibilityRole="button"
        style={styles.loginLink}
      >
        <Text style={styles.loginLinkTxt}>이미 계정이 있으면 로그인</Text>
      </Pressable>
    </SafeAreaView>
  );
}

// ── Visuals ──────────────────────────────────────────────────────────────────

function PrescriptionVisual() {
  return (
    <View style={styles.prescriptionWrap}>
      {/* 기울어진 처방전 카드 */}
      <View style={[styles.prescCard, styles.prescCardBack]}>
        <Text style={styles.prescPatient}>박○○ · 만 72세</Text>
        {['메트포르민정', '암로디핀정', '아스피린정', '리바록사반정'].map((d, i) => (
          <Text key={i} style={styles.prescDrug}>• {d}</Text>
        ))}
      </View>
      {/* AI 인식 결과 카드 */}
      <View style={styles.aiCard}>
        <Text style={styles.aiCardTitle}>✨ 보통 20~30초, 최대 1분</Text>
        <Text style={styles.aiCardSub}>4개 약 자동 등록됨</Text>
      </View>
    </View>
  );
}

function GroupVisual() {
  return (
    <View style={styles.groupWrap}>
      <View style={styles.groupRow}>
        {['보호자', '환자', '가족'].map((label, i) => (
          <View key={i} style={styles.memberChip}>
            <Text style={styles.memberIcon}>{['👨', '👴', '👩'][i]}</Text>
            <Text style={styles.memberLabel}>{label}</Text>
          </View>
        ))}
      </View>
      <View style={styles.groupCard}>
        <Text style={styles.groupCardTxt}>복약 현황 공유 중</Text>
        <Text style={styles.groupCardSub}>오늘 아침약 복용 완료 ✓</Text>
      </View>
    </View>
  );
}

function AlarmVisual() {
  return (
    <View style={styles.alarmWrap}>
      {[
        { time: '08:00', label: '아침', done: true },
        { time: '13:00', label: '점심', done: false },
        { time: '21:00', label: '저녁', done: false },
      ].map((slot) => (
        <View key={slot.label} style={styles.alarmRow}>
          <Text style={styles.alarmTime}>{slot.time}</Text>
          <Text style={styles.alarmLabel}>{slot.label}</Text>
          <View style={[styles.alarmBadge, slot.done && styles.alarmBadgeDone]}>
            <Text style={[styles.alarmBadgeTxt, slot.done && styles.alarmBadgeTxtDone]}>
              {slot.done ? '완료' : '예정'}
            </Text>
          </View>
        </View>
      ))}
    </View>
  );
}

// ── Styles ────────────────────────────────────────────────────────────────────

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bgNormal },
  header: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    paddingHorizontal: space.s20, paddingTop: space.s12, paddingBottom: space.s8,
  },
  logo: { fontSize: scale(17), fontWeight: '700', color: colors.labelNormal },
  skip: { fontSize: scale(14), color: colors.labelAlternative },

  visualArea: {
    flex: 1, alignItems: 'center', justifyContent: 'center',
    paddingHorizontal: space.s24,
  },

  textArea: { paddingHorizontal: space.s28, paddingBottom: space.s16, gap: space.s10 },
  heading: { ...typography.title2, color: colors.labelNormal, textAlign: 'center' },
  body: { ...typography.body2r, color: colors.labelAlternative, textAlign: 'center', lineHeight: scale(22) },

  dots: { flexDirection: 'row', justifyContent: 'center', gap: space.s6, paddingBottom: space.s20 },
  dot: { width: scale(7), height: scale(7), borderRadius: scale(4), backgroundColor: colors.fillNormal },
  dotActive: { width: scale(20), backgroundColor: colors.labelNormal },

  cta: {
    marginHorizontal: space.s20, paddingVertical: space.s16,
    backgroundColor: colors.labelNormal, borderRadius: radius.r16,
    alignItems: 'center',
  },
  ctaTxt: { fontSize: scale(16), fontWeight: '700', color: colors.staticWhite },

  loginLink: { alignItems: 'center', paddingVertical: space.s16 },
  loginLinkTxt: { fontSize: scale(13), color: colors.labelAlternative, textDecorationLine: 'underline' },

  evidenceWrap: {
    marginTop: space.s6,
    backgroundColor: colors.fillNormal,
    borderRadius: radius.r10,
    padding: space.s12,
    gap: space.s6,
  },
  evidenceStats: {
    ...typography.caption1,
    color: colors.labelAlternative,
    lineHeight: scale(18),
    textAlign: 'center',
  },
  evidenceSrc: {
    fontSize: scale(10),
    color: colors.labelAssistive,
    textAlign: 'center',
  },

  // PrescriptionVisual
  prescriptionWrap: { width: '100%', alignItems: 'center', position: 'relative', height: scale(220) },
  prescCard: {
    position: 'absolute', width: '78%',
    backgroundColor: colors.bgNormal, borderRadius: radius.r16,
    borderWidth: 1, borderColor: colors.line, padding: space.s16, gap: space.s6,
    shadowColor: '#000', shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.10, shadowRadius: 12, elevation: 4,
  },
  prescCardBack: { top: 0, transform: [{ rotate: '-4deg' }] },
  prescPatient: { fontSize: scale(13), fontWeight: '700', color: colors.labelNormal, marginBottom: space.s4 },
  prescDrug: { fontSize: scale(12), color: colors.labelAlternative },
  aiCard: {
    position: 'absolute', bottom: 0, right: space.s16,
    backgroundColor: colors.primaryNormal, borderRadius: radius.r14,
    paddingHorizontal: space.s16, paddingVertical: space.s12, gap: space.s2,
  },
  aiCardTitle: { fontSize: scale(13), fontWeight: '700', color: colors.staticWhite },
  aiCardSub: { fontSize: scale(12), color: colors.staticWhite, opacity: 0.85 },

  // GroupVisual
  groupWrap: { width: '100%', alignItems: 'center', gap: space.s20 },
  groupRow: { flexDirection: 'row', gap: space.s16 },
  memberChip: { alignItems: 'center', gap: space.s6 },
  memberIcon: { fontSize: scale(36) },
  memberLabel: { fontSize: scale(12), color: colors.labelAlternative },
  groupCard: {
    backgroundColor: colors.blue95, borderRadius: radius.r16,
    paddingHorizontal: space.s24, paddingVertical: space.s14,
    alignItems: 'center', gap: space.s4,
  },
  groupCardTxt: { fontSize: scale(14), fontWeight: '700', color: colors.primaryNormal },
  groupCardSub: { fontSize: scale(13), color: colors.primaryNormal, opacity: 0.8 },

  // AlarmVisual
  alarmWrap: {
    width: '100%', backgroundColor: colors.bgNormal,
    borderRadius: radius.r16, borderWidth: 1, borderColor: colors.line,
    overflow: 'hidden',
  },
  alarmRow: {
    flexDirection: 'row', alignItems: 'center',
    paddingHorizontal: space.s20, paddingVertical: space.s14,
    borderBottomWidth: 1, borderBottomColor: colors.line,
    gap: space.s12,
  },
  alarmTime: { fontSize: scale(16), fontWeight: '700', color: colors.labelNormal, width: scale(46) },
  alarmLabel: { flex: 1, fontSize: scale(13), color: colors.labelAlternative },
  alarmBadge: {
    paddingHorizontal: space.s10, paddingVertical: space.s4,
    borderRadius: radius.full, backgroundColor: colors.fillNormal,
  },
  alarmBadgeDone: { backgroundColor: colors.blue95 },
  alarmBadgeTxt: { fontSize: scale(12), color: colors.labelAlternative },
  alarmBadgeTxtDone: { color: colors.primaryNormal, fontWeight: '600' },
});
