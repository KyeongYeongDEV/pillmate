import React, { useState, useCallback, useRef } from 'react';
import {
  View, Text, ScrollView, TextInput, Pressable,
  StyleSheet, KeyboardAvoidingView, Platform,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Feather } from '@expo/vector-icons';
import AiBubble from '@/components/chat/AiBubble';
import UserBubble from '@/components/chat/UserBubble';
import { useSendMessageMutation } from '@/store/slices/chatApi';
import { scale, colors, space, radius } from '@/styles/tokens';
import TabHeader from '@/components/navigation/TabHeader';
import type { ChatMessage } from '@/types/chat';

// 비용 정책: AI 채팅은 출시 시점 비활성 (2026-06-11 사용자 명시). true 로 바꾸면 즉시 활성.
export const CHAT_ENABLED = false;

const QUICK_PROMPTS = ['감기약 같이 먹어도 돼?', '부작용은?', '음식 주의사항'];

const WELCOME_MESSAGE: ChatMessage = {
  id: 'ai-welcome',
  role: 'ai',
  content: '안녕하세요, PillMate AI예요 🤖\n약 상담 기능은 곧 오픈할 예정이에요.\n조금만 기다려주세요!',
};

const INITIAL_MESSAGES: ChatMessage[] = [
  {
    id: 'ai-greet',
    role: 'ai',
    content: '안녕하세요. 할머니가 복용 중인 약에 대해 궁금한 점이 있으시면 물어보세요.',
  },
  {
    id: 'user-1',
    role: 'user',
    content: '엄마가 혈압약 먹는데 감기약 같이 드셔도 되나요?',
  },
  {
    id: 'ai-1',
    role: 'ai',
    hasWarning: true,
    warningText: '일부 감기약은 주의가 필요해요',
    content: '암로디핀은 일반 감기약과 대체로 함께 복용 가능하지만, 슈도에페드린 성분이 포함된 감기약은 혈압을 올릴 수 있어 피해야 합니다.',
    sources: [
      { organization: '식약처 의약품안전나라', document: '암로디핀정 병용주의' },
      { organization: '대한고혈압학회', document: '고혈압 환자의 감기약 복용 지침 2024' },
    ],
  },
];

export default function ChatScreen() {
  const [messages, setMessages] = useState<ChatMessage[]>(
    CHAT_ENABLED ? INITIAL_MESSAGES : [WELCOME_MESSAGE],
  );
  const [input, setInput] = useState('');
  const scrollRef = useRef<ScrollView>(null);
  const [sendMessage, { isLoading }] = useSendMessageMutation();

  const handleSend = useCallback(async (text: string) => {
    if (!CHAT_ENABLED) return;
    const trimmed = text.trim();
    if (!trimmed) return;
    setInput('');
    const userMsg: ChatMessage = { id: `user-${Date.now()}`, role: 'user', content: trimmed };
    setMessages(prev => [...prev, userMsg]);

    const result = await sendMessage({ message: trimmed, patientId: 1 });
    if ('data' in result && result.data) {
      const d = result.data;
      const ai: ChatMessage = {
        id: `ai-${Date.now()}`,
        role: 'ai',
        content: d.content,
        sources: d.sources,
        hasWarning: d.hasWarning,
        warningText: d.warningText,
      };
      setMessages(prev => [...prev, ai]);
    }
    scrollRef.current?.scrollToEnd({ animated: true });
  }, [sendMessage]);

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <TabHeader
        title="복약 상담"
        subtitle={CHAT_ENABLED ? '● Gemini · RAG 검증' : '곧 오픈 예정'}
        right={
          <Pressable accessibilityLabel="더 보기" accessibilityRole="button">
            <Feather name="more-horizontal" size={scale(22)} color={colors.labelNormal} />
          </Pressable>
        }
      />

      <KeyboardAvoidingView style={styles.kav} behavior={Platform.OS === 'ios' ? 'padding' : 'height'}>
        <ScrollView ref={scrollRef} style={styles.scroll} contentContainerStyle={styles.scrollContent}>
          {messages.map((msg) =>
            msg.role === 'ai'
              ? <AiBubble key={msg.id} message={msg} />
              : <UserBubble key={msg.id} message={msg} />,
          )}
          {CHAT_ENABLED && messages.length === 1 && (
            <View style={styles.quickChips}>
              {QUICK_PROMPTS.map(q => (
                <Pressable key={q} style={styles.chip} onPress={() => handleSend(q)}>
                  <Text style={styles.chipText}>{q}</Text>
                </Pressable>
              ))}
            </View>
          )}
        </ScrollView>

        <View style={styles.inputBar}>
          {CHAT_ENABLED ? (
            <Pressable style={styles.attachBtn} accessibilityLabel="파일 첨부" accessibilityRole="button">
              <Feather name="plus" size={scale(22)} color={colors.labelAlternative} />
            </Pressable>
          ) : (
            <Text style={styles.lockIcon}>🔒</Text>
          )}
          <TextInput
            style={[styles.input, !CHAT_ENABLED && styles.inputLocked]}
            value={input}
            onChangeText={CHAT_ENABLED ? setInput : undefined}
            editable={CHAT_ENABLED}
            placeholder={CHAT_ENABLED ? '약에 대해 물어보세요…' : '준비 중인 기능이에요'}
            placeholderTextColor={colors.labelAssistive}
            returnKeyType="send"
            onSubmitEditing={CHAT_ENABLED ? () => handleSend(input) : undefined}
            accessibilityLabel="메시지 입력"
          />
          <Pressable
            style={[styles.sendBtn, (!CHAT_ENABLED || isLoading) && styles.sendBtnDisabled]}
            onPress={CHAT_ENABLED ? () => handleSend(input) : undefined}
            disabled={!CHAT_ENABLED || isLoading}
            accessibilityLabel="전송"
            accessibilityRole="button"
          >
            <Feather name="send" size={scale(20)} color="#fff" />
          </Pressable>
        </View>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bgAlt },
  kav: { flex: 1 },
  scroll: { flex: 1 },
  scrollContent: { padding: space.s16, gap: space.s14, paddingBottom: space.s8 },
  quickChips: { flexDirection: 'row', flexWrap: 'wrap', gap: space.s6, marginTop: space.s10 },
  chip: {
    paddingHorizontal: space.s12, paddingVertical: 7,
    borderRadius: radius.full, backgroundColor: colors.bgNormal,
    borderWidth: 1, borderColor: colors.line,
  },
  chipText: { fontSize: scale(12), color: colors.labelNormal, fontWeight: '500' },
  inputBar: {
    flexDirection: 'row', alignItems: 'center', gap: space.s8,
    paddingHorizontal: space.s16, paddingTop: space.s10, paddingBottom: space.s28,
    backgroundColor: colors.bgNormal, borderTopWidth: 1, borderTopColor: colors.line,
  },
  attachBtn: {
    width: scale(38), height: scale(38), borderRadius: scale(19),
    backgroundColor: colors.fillNormal, alignItems: 'center', justifyContent: 'center',
  },
  lockIcon: { fontSize: scale(20), width: scale(38), textAlign: 'center' },
  input: {
    flex: 1, height: scale(42), borderRadius: radius.full,
    backgroundColor: colors.bgAlt, borderWidth: 1, borderColor: colors.line,
    paddingHorizontal: space.s16, fontSize: scale(14), color: colors.labelNormal,
  },
  inputLocked: { backgroundColor: colors.fillNormal, borderColor: colors.line, color: colors.labelAssistive },
  sendBtn: {
    width: scale(42), height: scale(42), borderRadius: scale(21),
    backgroundColor: colors.primaryBase, alignItems: 'center', justifyContent: 'center',
  },
  sendBtnDisabled: { opacity: 0.4 },
});
