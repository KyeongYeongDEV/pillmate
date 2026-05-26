import React, { useState } from 'react';
import { View, Text, Pressable, StyleSheet } from 'react-native';
import { colors, space, typography } from '@/styles/tokens';

export type TabId = 'efficacy' | 'dosage' | 'warnings';

const TABS: { id: TabId; label: string }[] = [
  { id: 'efficacy', label: '효능·효과' },
  { id: 'dosage',   label: '용법·용량' },
  { id: 'warnings', label: '주의사항' },
];

interface DetailTabsProps {
  efficacy: string[];
  dosage: string[];
  warnings: string[];
}

export default function DetailTabs({ efficacy, dosage, warnings }: DetailTabsProps) {
  const [activeTab, setActiveTab] = useState<TabId>('efficacy');

  const content: Record<TabId, string[]> = { efficacy, dosage, warnings };
  const items = content[activeTab];

  return (
    <View>
      {/* Tab bar */}
      <View style={styles.tabBar}>
        {TABS.map(tab => (
          <Pressable
            key={tab.id}
            style={[styles.tab, activeTab === tab.id && styles.tabActive]}
            onPress={() => setActiveTab(tab.id)}
            accessibilityRole="tab"
            accessibilityLabel={tab.label}
            accessibilityState={{ selected: activeTab === tab.id }}
          >
            <Text style={[styles.tabLabel, activeTab === tab.id && styles.tabLabelActive]}>
              {tab.label}
            </Text>
          </Pressable>
        ))}
      </View>

      {/* Content */}
      <View style={styles.content}>
        <Text style={styles.sectionTitle}>
          {activeTab === 'efficacy' ? '이런 분께 처방돼요' :
           activeTab === 'dosage'   ? '복용 방법' : '주의해야 할 사항'}
        </Text>
        {items.map((item, idx) => (
          <View key={idx} style={styles.bullet}>
            <View style={styles.dot} />
            <Text style={styles.bulletText}>{item}</Text>
          </View>
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  tabBar: {
    flexDirection: 'row', borderBottomWidth: 1, borderBottomColor: colors.line,
    marginHorizontal: space.s16, marginBottom: space.s4,
  },
  tab: {
    flex: 1, paddingBottom: space.s12, alignItems: 'center',
    borderBottomWidth: 2, borderBottomColor: 'transparent', marginBottom: -1,
  },
  tabActive: { borderBottomColor: colors.labelNormal },
  tabLabel: { ...typography.label1n, fontWeight: '700', color: colors.labelAlternative, letterSpacing: -0.005 },
  tabLabelActive: { color: colors.labelNormal },
  content: { paddingHorizontal: space.s16, paddingTop: space.s20, paddingBottom: space.s16 },
  sectionTitle: { ...typography.body1n, fontWeight: '700', marginBottom: space.s10, letterSpacing: -0.015 },
  bullet: { flexDirection: 'row', gap: space.s10, paddingVertical: space.s8 },
  dot: { width: 5, height: 5, borderRadius: 9999, backgroundColor: colors.labelNormal, marginTop: 9, flexShrink: 0 },
  bulletText: { flex: 1, ...typography.label1n, color: colors.labelAlternative, lineHeight: 22 },
});
