import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import Avatar from '@/components/common/Avatar';
import { scale, colors, space, radius } from '@/styles/tokens';
import type { ChatMessage } from '@/types/chat';

interface Props {
  message: ChatMessage;
}

const CONSULT_FOOTER = '정확한 정보는 담당 약사·의사와 상담하세요.';

function AiBubble({ message }: Props) {
  return (
    <View style={styles.row}>
      <Avatar name="P" tint={colors.primaryBase} size={scale(32)} />
      <View style={styles.body}>
        <Text style={styles.senderLabel}>PillMate AI</Text>
        <View style={styles.bubble}>
          {message.hasWarning && message.warningText && (
            <Text style={styles.warningText}>⚠ {message.warningText}</Text>
          )}
          <Text style={styles.content}>{message.content}</Text>

          {message.sources && message.sources.length > 0 && (
            <View style={styles.sourceSection}>
              <Text style={styles.sourceHeader}>출처 · {message.sources.length}개</Text>
              {message.sources.map((s, i) => (
                <View key={i} style={styles.sourceRow}>
                  <View style={styles.sourceIdx}>
                    <Text style={styles.sourceIdxText}>{i + 1}</Text>
                  </View>
                  <View style={styles.sourceInfo}>
                    <Text style={styles.sourceOrg}>{s.organization}</Text>
                    <Text style={styles.sourceDoc}>{s.document}</Text>
                  </View>
                </View>
              ))}
            </View>
          )}

          {/* medical safety: consulting footer always present */}
          <Text style={styles.consultFooter}>{CONSULT_FOOTER}</Text>
        </View>
      </View>
    </View>
  );
}

export default React.memo(AiBubble);

const styles = StyleSheet.create({
  row: { flexDirection: 'row', gap: space.s10, alignItems: 'flex-start' },
  body: { flex: 1, maxWidth: scale(290) },
  senderLabel: { fontSize: scale(11), color: colors.labelAlternative, fontWeight: '600', marginBottom: 4 },
  bubble: {
    backgroundColor: colors.bgNormal, borderRadius: radius.r14,
    padding: space.s14, borderWidth: 1, borderColor: colors.line,
    gap: space.s8,
  },
  warningText: { fontSize: scale(13), fontWeight: '700', color: colors.orange40 },
  content: { fontSize: scale(14), color: colors.labelNormal, lineHeight: scale(22) },
  sourceSection: { borderTopWidth: 1, borderTopColor: colors.line, paddingTop: space.s10, gap: 4 },
  sourceHeader: { fontSize: scale(11), color: colors.labelAlternative, fontWeight: '600', marginBottom: 2 },
  sourceRow: { flexDirection: 'row', gap: space.s8, alignItems: 'flex-start' },
  sourceIdx: {
    width: scale(18), height: scale(18), borderRadius: scale(4), flexShrink: 0,
    backgroundColor: colors.blue95, alignItems: 'center', justifyContent: 'center',
  },
  sourceIdxText: { fontSize: scale(10), fontWeight: '700', color: colors.primaryNormal },
  sourceInfo: { flex: 1 },
  sourceOrg: { fontSize: scale(12), fontWeight: '600', color: colors.labelNormal },
  sourceDoc: { fontSize: scale(11), color: colors.labelAlternative, marginTop: 1 },
  consultFooter: { fontSize: scale(11), color: colors.labelAlternative, lineHeight: scale(16), borderTopWidth: 1, borderTopColor: colors.line, paddingTop: space.s8 },
});
