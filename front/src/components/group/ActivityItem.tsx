import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import Avatar from '@/components/common/Avatar';
import { colors, space, radius } from '@/styles/tokens';
import type { GroupActivity } from '@/types/group';

const KIND_DOT: Record<string, string> = {
  done: colors.statusPositive,
  miss: colors.statusNegative,
  ai:   colors.violet45,
  rx:   colors.primaryBase,
  note: colors.cyan50,
};

interface Props {
  item: GroupActivity;
  isLast?: boolean;
}

function ActivityItem({ item, isLast }: Props) {
  return (
    <View style={styles.wrapper}>
      {/* timeline rail */}
      <View style={styles.rail}>
        {!isLast && <View style={styles.line} />}
        <View style={[styles.dot, { backgroundColor: KIND_DOT[item.kind] ?? colors.labelAlternative }]} />
      </View>

      {/* card */}
      <View style={[styles.card, isLast ? styles.cardLast : styles.cardSpaced]}>
        <View style={styles.cardHead}>
          <Avatar name={item.who[0]} tint={item.tint} size={24} />
          <Text style={styles.who}>
            <Text style={styles.whoName}>{item.who}</Text>
            <Text style={styles.whoLabel}> · {item.whoLabel}</Text>
          </Text>
          <Text style={styles.time}>{item.time}</Text>
        </View>
        <Text style={styles.title}>{item.title}</Text>
        {Array.isArray(item.detail) ? (
          <View style={styles.detailBox}>
            <Text style={styles.detailText}>{item.detail.join(' · ')}</Text>
          </View>
        ) : (
          <Text style={styles.detailStr}>{item.detail}</Text>
        )}
        {item.cta && (
          <View style={styles.ctaBtn}>
            <Text style={styles.ctaText}>{item.cta}</Text>
          </View>
        )}
      </View>
    </View>
  );
}

export default React.memo(ActivityItem);

const styles = StyleSheet.create({
  wrapper: { flexDirection: 'row', gap: space.s12 },
  rail: { width: 14, alignItems: 'center', paddingTop: 16 },
  line: { position: 'absolute', top: 20, bottom: -2, width: 2, backgroundColor: colors.line, left: 6 },
  dot: { width: 10, height: 10, borderRadius: 5 },
  card: { flex: 1, backgroundColor: colors.bgNormal, borderRadius: radius.r12, padding: space.s12, borderWidth: 1, borderColor: colors.line, gap: space.s6 },
  cardSpaced: { marginBottom: space.s10 },
  cardLast: {},
  cardHead: { flexDirection: 'row', alignItems: 'center', gap: space.s8 },
  who: { flex: 1, fontSize: 12 },
  whoName: { fontWeight: '700', color: colors.labelNormal },
  whoLabel: { color: colors.labelAlternative },
  time: { fontSize: 11, color: colors.labelAlternative, fontWeight: '500' },
  title: { fontSize: 14, fontWeight: '700', color: colors.labelNormal, letterSpacing: -0.01, lineHeight: 19 },
  detailBox: { backgroundColor: colors.bgAlt, borderRadius: radius.r8, padding: space.s8 },
  detailText: { fontSize: 12, color: colors.labelAlternative, lineHeight: 17 },
  detailStr: { fontSize: 13, color: colors.labelAlternative, lineHeight: 19 },
  ctaBtn: { alignSelf: 'flex-start', paddingHorizontal: space.s12, paddingVertical: space.s6, borderRadius: radius.r8, backgroundColor: colors.labelNormal },
  ctaText: { fontSize: 12, fontWeight: '600', color: colors.staticWhite },
});
