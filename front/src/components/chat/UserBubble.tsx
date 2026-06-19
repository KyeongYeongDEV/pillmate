import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { scale, colors, space, radius } from '@/styles/tokens';
import type { ChatMessage } from '@/types/chat';

interface Props {
  message: ChatMessage;
}

function UserBubble({ message }: Props) {
  return (
    <View style={styles.row}>
      <View style={styles.bubble}>
        <Text style={styles.content}>{message.content}</Text>
      </View>
    </View>
  );
}

export default React.memo(UserBubble);

const styles = StyleSheet.create({
  row: { flexDirection: 'row', justifyContent: 'flex-end' },
  bubble: {
    backgroundColor: colors.primaryBase,
    borderRadius: radius.r14,
    padding: space.s12,
    maxWidth: scale(280),
  },
  content: { fontSize: scale(14), color: '#fff', lineHeight: scale(21) },
});
