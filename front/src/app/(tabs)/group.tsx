import React, { useCallback } from 'react';
import { View, Text, ScrollView, StyleSheet, Pressable } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Feather } from '@expo/vector-icons';
import Avatar from '@/components/common/Avatar';
import MemberCard from '@/components/group/MemberCard';
import ActivityItem from '@/components/group/ActivityItem';
import { colors, space, radius, typography } from '@/styles/tokens';
import type { GroupMember, GroupActivity } from '@/types/group';

const MEMBERS: GroupMember[] = [
  { id: '1', name: '박순자', sub: '환자 · 만 72세', role: '환자',  tint: '#FF7B2E', online: true },
  { id: '2', name: '김민지', sub: '딸 · 본인',       role: '보호자', tint: '#0066FF', online: true,  isMe: true },
  { id: '3', name: '김지훈', sub: '아들',             role: '보호자', tint: '#6541F2', online: false },
];

const ACTIVITIES: GroupActivity[] = [
  { id: '1', who: '박순자', whoLabel: '할머니', tint: '#FF7B2E', time: '오늘 12:34', kind: 'done',  title: '점심약 2개를 복용했어요', detail: ['메트포르민 500mg', '글리메피리드 2mg'], pills: ['#FF7B2E', '#fff'] },
  { id: '2', who: 'PillMate AI', whoLabel: 'AI', tint: '#6541F2', time: '오늘 09:10', kind: 'ai', title: '저녁약 미복용 패턴', detail: '지난 7일 중 3일 빠뜨리셨어요. 알림 시간을 조정해볼까요?', cta: '알림 조정' },
  { id: '3', who: '김민지', whoLabel: '딸', tint: '#0066FF', time: '오늘 07:40', kind: 'rx',  title: '새 처방전을 등록했어요', detail: '내과 진료 · 약 5개 추가' },
  { id: '4', who: '박순자', whoLabel: '할머니', tint: '#FF7B2E', time: '어제 22:30', kind: 'miss', title: '취침 전 약을 놓치셨어요', detail: ['오메가-3 1000mg'] },
  { id: '5', who: '김지훈', whoLabel: '아들', tint: '#6541F2', time: '어제 20:14', kind: 'note', title: '메모를 남겼어요', detail: '"엄마, 오늘 어지러우셨다고 하셨어요. 다음 진료에서 여쭤봐요."' },
];

export default function GroupScreen() {
  const handleInvite = useCallback(() => { /* Phase 2: invite sheet */ }, []);
  const handleQr    = useCallback(() => { /* Phase 2: QR sheet */ }, []);
  const handleCopy  = useCallback(() => { /* Phase 2: clipboard */ }, []);
  // Phase 2-FE: patientId 로 처방전/스케줄 조회 화면 진입
  const handleMemberPress = useCallback((_member: GroupMember) => { /* Phase 2: router.push(`/patient/${member.id}`) */ }, []);

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <View style={styles.header}>
        <Text style={styles.headerTitle}>케어 그룹</Text>
        <Pressable accessibilityLabel="그룹 설정" accessibilityRole="button">
          <Feather name="settings" size={22} color={colors.labelNormal} />
        </Pressable>
      </View>

      <ScrollView style={styles.scroll} contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        {/* group hero */}
        <View style={styles.heroCard}>
          <View style={styles.heroTop}>
            <View style={styles.avatarStack}>
              <View style={styles.av0}><Avatar name="박" tint="#FF7B2E" size={44} /></View>
              <View style={styles.av1}><Avatar name="민" tint="#0066FF" size={44} /></View>
              <View style={styles.avMore}><Text style={styles.avMoreText}>+1</Text></View>
            </View>
            <View style={styles.heroInfo}>
              <Text style={styles.heroName}>할머니 댁</Text>
              <Text style={styles.heroSub}>3명 · 보호자 2 · 환자 1</Text>
            </View>
          </View>
          <View style={styles.heroActions}>
            <Pressable style={styles.inviteBtn} onPress={handleInvite} accessibilityLabel="초대하기" accessibilityRole="button">
              <Feather name="plus" size={18} color="#fff" />
              <Text style={styles.inviteBtnText}>초대하기</Text>
            </Pressable>
            <Pressable style={styles.qrBtn} onPress={handleQr} accessibilityLabel="QR 코드" accessibilityRole="button">
              <Feather name="grid" size={20} color={colors.labelNormal} />
            </Pressable>
          </View>
        </View>

        {/* members */}
        <Text style={styles.sectionLabel}>구성원 · {MEMBERS.length}</Text>
        <View style={styles.listCard}>
          {MEMBERS.map((m, i) => (
            <MemberCard key={m.id} member={m} isFirst={i === 0} onPress={handleMemberPress} />
          ))}
        </View>

        {/* invite code */}
        <View style={styles.inviteCodeCard}>
          <View style={styles.inviteCodeRow}>
            <View>
              <Text style={styles.inviteCodeLabel}>초대 코드</Text>
              <Text style={styles.inviteCode}>3F9-K2P</Text>
            </View>
            <Pressable style={styles.copyBtn} onPress={handleCopy} accessibilityLabel="초대 코드 복사" accessibilityRole="button">
              <Text style={styles.copyText}>복사</Text>
            </Pressable>
          </View>
          <Text style={styles.inviteExpiry}>유효 시간 23분 · 가족에게 코드 또는 QR을 전송하세요.</Text>
        </View>

        {/* activity */}
        <View style={styles.activityHeader}>
          <Text style={styles.activityTitle}>그룹 활동</Text>
          <Text style={styles.activityAll}>전체보기</Text>
        </View>
        <Text style={styles.activitySub}>최근 일주일</Text>
        <View style={styles.activityList}>
          {ACTIVITIES.map((item, i) => (
            <ActivityItem key={item.id} item={item} isLast={i === ACTIVITIES.length - 1} />
          ))}
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bgAlt },
  header: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    paddingHorizontal: space.s16, paddingVertical: space.s12,
    backgroundColor: colors.bgNormal, borderBottomWidth: 1, borderBottomColor: colors.line,
  },
  headerTitle: { ...typography.headline1, color: colors.labelNormal },
  scroll: { flex: 1 },
  content: { padding: space.s16, gap: space.s16, paddingBottom: 80 },
  heroCard: {
    backgroundColor: colors.bgNormal, borderRadius: radius.r20,
    padding: 22, borderWidth: 1, borderColor: colors.line, gap: space.s18,
  },
  heroTop: { flexDirection: 'row', alignItems: 'center', gap: space.s14 },
  avatarStack: { position: 'relative', width: 88, height: 44 },
  av0: { position: 'absolute', left: 0, top: 0 },
  av1: { position: 'absolute', left: 20, top: 0 },
  avMore: {
    position: 'absolute', left: 40, top: 0, width: 44, height: 44,
    borderRadius: 22, backgroundColor: colors.bgNormal,
    borderWidth: 1.5, borderColor: colors.line,
    alignItems: 'center', justifyContent: 'center',
  },
  avMoreText: { fontSize: 14, fontWeight: '700', color: colors.labelAlternative },
  heroInfo: { flex: 1, marginLeft: space.s28 },
  heroName: { fontSize: 18, fontWeight: '700', letterSpacing: -0.015, color: colors.labelNormal },
  heroSub: { fontSize: 13, color: colors.labelAlternative, marginTop: 2 },
  heroActions: { flexDirection: 'row', gap: space.s8 },
  inviteBtn: {
    flex: 1, height: 42, borderRadius: radius.r10,
    backgroundColor: colors.labelNormal, flexDirection: 'row',
    alignItems: 'center', justifyContent: 'center', gap: space.s6,
  },
  inviteBtnText: { fontSize: 14, fontWeight: '600', color: '#fff' },
  qrBtn: {
    width: 42, height: 42, borderRadius: radius.r10,
    backgroundColor: colors.fillNormal, alignItems: 'center', justifyContent: 'center',
  },
  sectionLabel: { fontSize: 11, fontWeight: '700', color: colors.labelAlternative, letterSpacing: 0.06 },
  listCard: {
    backgroundColor: colors.bgNormal, borderRadius: radius.r16,
    borderWidth: 1, borderColor: colors.line, overflow: 'hidden',
  },
  inviteCodeCard: {
    backgroundColor: colors.bgNormal, borderRadius: radius.r16,
    padding: space.s18, borderWidth: 1, borderStyle: 'dashed', borderColor: colors.line,
  },
  inviteCodeRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  inviteCodeLabel: { fontSize: 11, color: colors.labelAlternative, fontWeight: '600', letterSpacing: 0.04 },
  inviteCode: { fontSize: 22, fontWeight: '700', letterSpacing: 0.08, marginTop: 4, color: colors.labelNormal },
  copyBtn: {
    paddingHorizontal: space.s14, paddingVertical: space.s8,
    borderRadius: radius.full, backgroundColor: colors.fillNormal,
  },
  copyText: { fontSize: 13, fontWeight: '600', color: colors.labelNormal },
  inviteExpiry: { fontSize: 12, color: colors.labelAlternative, marginTop: space.s8, lineHeight: 17 },
  activityHeader: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 0 },
  activityTitle: { fontSize: 13, fontWeight: '700', color: colors.labelNormal, letterSpacing: -0.01 },
  activityAll: { fontSize: 12, color: colors.primaryBase, fontWeight: '600' },
  activitySub: { fontSize: 11, color: colors.labelAlternative, marginTop: -space.s8 },
  activityList: {},
});
