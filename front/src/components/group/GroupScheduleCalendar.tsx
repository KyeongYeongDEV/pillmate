import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { View, Text, Pressable, StyleSheet } from 'react-native';
import { router } from 'expo-router';
import { Feather } from '@expo/vector-icons';
import { scale, colors, space, radius } from '@/styles/tokens';
import { getCurrentUserId } from '@/lib/auth/storage';
import { buildCalendarRows, toMonthString, prevMonth, nextMonth, getKstToday } from '@/utils/calendarUtils';
import { assignMemberColors } from '@/utils/memberColors';
import { useGetGroupMonthScheduleQuery, type GroupMemberAdherence } from '@/store/slices/caregroupApi';
import type { MemberView } from '@/types/caregroup';

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];
const MAX_DOTS_PER_CELL = 5;
const LOAD_FAILED_MSG = '그룹 복약 현황을 불러올 수 없어요';
const RETRY_LABEL = '재시도';

// 멤버 고유색은 고정하고, adherence 는 점의 형태/투명도로만 구분한다 —
// 그래야 "색 = 사람" 매핑이 날짜·상태와 무관하게 항상 성립한다.
const ADHERENCE_OPACITY: Record<GroupMemberAdherence['adherence'], number> = {
  FULL: 1,
  PARTIAL: 0.5,
  MISS: 1,
  UPCOMING: 0.28,
};

export interface GroupScheduleCalendarProps {
  groupId: number;
  members: MemberView[];
}

export default function GroupScheduleCalendar({ groupId, members }: GroupScheduleCalendarProps) {
  const [displayYear, setDisplayYear] = useState(() => Number(getKstToday().slice(0, 4)));
  const [displayMonth, setDisplayMonth] = useState(() => Number(getKstToday().slice(5, 7)));
  const [currentUserId, setCurrentUserId] = useState<number | null>(null);

  useEffect(() => {
    let active = true;
    getCurrentUserId().then(uid => { if (active) setCurrentUserId(uid); });
    return () => { active = false; };
  }, []);

  const {
    data: adherenceByDate, error, refetch,
  } = useGetGroupMonthScheduleQuery(
    { groupId, month: toMonthString(displayYear, displayMonth) },
    { skip: !Number.isFinite(groupId) },
  );

  const colorByUserId = useMemo(() => assignMemberColors(members), [members]);
  const rows = useMemo(() => buildCalendarRows(displayYear, displayMonth), [displayYear, displayMonth]);

  const handlePrevMonth = useCallback(() => {
    const { year, month } = prevMonth(displayYear, displayMonth);
    setDisplayYear(year);
    setDisplayMonth(month);
  }, [displayYear, displayMonth]);

  const handleNextMonth = useCallback(() => {
    const { year, month } = nextMonth(displayYear, displayMonth);
    setDisplayYear(year);
    setDisplayMonth(month);
  }, [displayYear, displayMonth]);

  const handleMemberPress = useCallback((member: MemberView) => {
    router.push(`/group/${groupId}/member/${member.userId}?name=${encodeURIComponent(member.name)}` as any);
  }, [groupId]);

  if (error) {
    return (
      <View style={styles.noticeBox}>
        <Text style={styles.noticeText}>{LOAD_FAILED_MSG}</Text>
        <Pressable
          style={styles.retryBtn}
          onPress={() => refetch()}
          accessibilityLabel={RETRY_LABEL}
          accessibilityRole="button"
        >
          <Text style={styles.retryTxt}>{RETRY_LABEL}</Text>
        </Pressable>
      </View>
    );
  }

  return (
    <View style={styles.card}>
      <View style={styles.monthRow}>
        <Text style={styles.monthTitle}>{displayYear}년 {displayMonth}월</Text>
        <View style={styles.chevrons}>
          <Pressable
            style={styles.chevronBtn}
            onPress={handlePrevMonth}
            accessibilityLabel="이전 달"
            accessibilityRole="button"
          >
            <Feather name="chevron-left" size={scale(18)} color={colors.labelNormal} />
          </Pressable>
          <Pressable
            style={styles.chevronBtn}
            onPress={handleNextMonth}
            accessibilityLabel="다음 달"
            accessibilityRole="button"
          >
            <Feather name="chevron-right" size={scale(18)} color={colors.labelNormal} />
          </Pressable>
        </View>
      </View>

      <View style={styles.row}>
        {WEEKDAYS.map((d, i) => (
          <View key={d} style={styles.cell}>
            <Text style={[styles.wdLabel, i === 0 && styles.sunText, i === 6 && styles.satText]}>{d}</Text>
          </View>
        ))}
      </View>

      {rows.map((row, ri) => (
        <View key={ri} style={styles.row}>
          {row.map((dateStr, ci) => (
            <DayCell
              key={ci}
              dateStr={dateStr}
              col={ci}
              members={dateStr ? adherenceByDate?.[dateStr] : undefined}
              colorByUserId={colorByUserId}
            />
          ))}
        </View>
      ))}

      <View style={styles.legend}>
        {members.map((member) => {
          const swatch = colorByUserId.get(member.userId) ?? colors.fallbackGray;
          const isMe = member.userId === currentUserId;
          if (isMe) {
            return (
              <View key={member.userId} style={styles.legendItem}>
                <View testID={`member-swatch-${member.userId}`} style={[styles.legendSwatch, { backgroundColor: swatch }]} />
                <Text style={styles.legendText}>{member.name} (나)</Text>
              </View>
            );
          }
          return (
            <Pressable
              key={member.userId}
              style={styles.legendItem}
              onPress={() => handleMemberPress(member)}
              accessibilityLabel={`${member.name} 복약 보기`}
              accessibilityRole="button"
            >
              <View testID={`member-swatch-${member.userId}`} style={[styles.legendSwatch, { backgroundColor: swatch }]} />
              <Text style={styles.legendText}>{member.name}</Text>
            </Pressable>
          );
        })}
      </View>
    </View>
  );
}

interface DayCellProps {
  dateStr: string | null;
  col: number;
  members?: GroupMemberAdherence[];
  colorByUserId: Map<number, string>;
}

function DayCell({ dateStr, col, members, colorByUserId }: DayCellProps) {
  if (dateStr === null) return <View style={styles.cell} />;

  const day = parseInt(dateStr.slice(8), 10);
  const shown = members?.slice(0, MAX_DOTS_PER_CELL) ?? [];

  return (
    <View style={[styles.cell, styles.dayCell]}>
      <Text style={[styles.numText, col === 0 && styles.sunText, col === 6 && styles.satText]}>{day}</Text>
      <View style={styles.dotRow}>
        {shown.map((m, i) => (
          <MemberDot
            key={`${m.userId}-${i}`}
            color={colorByUserId.get(m.userId) ?? colors.fallbackGray}
            adherence={m.adherence}
          />
        ))}
      </View>
    </View>
  );
}

function MemberDot({ color, adherence }: { color: string; adherence: GroupMemberAdherence['adherence'] }) {
  // MISS 는 테두리만 있는 빈 원, 나머지는 채운 원(투명도로 강약).
  if (adherence === 'MISS') {
    return <View style={[styles.dot, styles.dotHollow, { borderColor: color }]} />;
  }
  return <View style={[styles.dot, { backgroundColor: color, opacity: ADHERENCE_OPACITY[adherence] }]} />;
}

const DOT_SIZE = scale(6);

const styles = StyleSheet.create({
  card: {
    backgroundColor: colors.bgNormal, borderRadius: radius.r16,
    borderWidth: 1, borderColor: colors.line, paddingVertical: space.s12,
  },
  monthRow: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    paddingHorizontal: space.s16, paddingBottom: space.s12,
  },
  monthTitle: { fontSize: scale(16), fontWeight: '700', letterSpacing: -0.012, color: colors.labelNormal },
  chevrons: { flexDirection: 'row', gap: space.s4 },
  chevronBtn: {
    width: scale(32), height: scale(32), borderRadius: radius.r8,
    backgroundColor: colors.fillNormal, alignItems: 'center', justifyContent: 'center',
  },
  row: { flexDirection: 'row', paddingHorizontal: space.s8 },
  cell: { flex: 1, alignItems: 'center' },
  dayCell: { paddingVertical: 3, minHeight: scale(44) },
  numText: { fontSize: scale(13), fontWeight: '500', color: colors.labelNormal },
  sunText: { color: colors.statusNegative },
  satText: { color: colors.primaryBase },
  wdLabel: { fontSize: scale(11), fontWeight: '600', paddingVertical: 6, color: colors.labelAlternative },
  dotRow: {
    flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'center',
    gap: 2, marginTop: 3, maxWidth: scale(38),
  },
  dot: { width: DOT_SIZE, height: DOT_SIZE, borderRadius: DOT_SIZE / 2 },
  dotHollow: { backgroundColor: 'transparent', borderWidth: 1 },
  legend: {
    flexDirection: 'row', flexWrap: 'wrap', columnGap: space.s14, rowGap: space.s8,
    paddingHorizontal: space.s16, paddingTop: space.s14,
  },
  legendItem: { flexDirection: 'row', alignItems: 'center', gap: 5 },
  legendSwatch: { width: scale(10), height: scale(10), borderRadius: scale(5) },
  legendText: { fontSize: scale(12), color: colors.labelAlternative },
  noticeBox: {
    padding: space.s20, borderRadius: radius.r16,
    backgroundColor: colors.bgNormal, borderWidth: 1, borderColor: colors.line,
    alignItems: 'center', gap: space.s12,
  },
  noticeText: { fontSize: scale(14), color: colors.labelAlternative, textAlign: 'center' },
  retryBtn: {
    paddingHorizontal: space.s20, paddingVertical: space.s10,
    borderRadius: radius.r12, backgroundColor: colors.primaryNormal,
  },
  retryTxt: { fontSize: scale(13), fontWeight: '700', color: '#fff' },
});
