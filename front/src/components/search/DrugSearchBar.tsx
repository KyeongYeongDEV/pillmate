import React from 'react';
import { View, TextInput, Pressable, StyleSheet } from 'react-native';
import { Feather } from '@expo/vector-icons';
import { colors, space, radius } from '@/styles/tokens';

interface DrugSearchBarProps {
  value: string;
  onChangeText: (text: string) => void;
  onClear: () => void;
  autoFocus?: boolean;
}

export default function DrugSearchBar({ value, onChangeText, onClear, autoFocus }: DrugSearchBarProps) {
  return (
    <View style={styles.container}>
      <Feather name="search" size={20} color={colors.labelAlternative} />
      <TextInput
        style={styles.input}
        value={value}
        onChangeText={onChangeText}
        placeholder="약 이름, 성분, 효능으로 검색"
        placeholderTextColor={colors.labelAssistive}
        autoFocus={autoFocus}
        returnKeyType="search"
        accessibilityLabel="약 검색"
        accessibilityRole="search"
      />
      {value.length > 0 && (
        <Pressable
          style={styles.clearBtn}
          onPress={onClear}
          accessibilityLabel="검색어 지우기"
          accessibilityRole="button"
        >
          <Feather name="x" size={12} color="#fff" />
        </Pressable>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1, height: 44, borderRadius: radius.r12,
    backgroundColor: colors.fillNormal, borderWidth: 1.5, borderColor: colors.primaryBase,
    flexDirection: 'row', alignItems: 'center', paddingHorizontal: space.s14, gap: space.s10,
  },
  input: {
    flex: 1, fontSize: 15, color: colors.labelNormal, fontWeight: '500',
  },
  clearBtn: {
    width: 18, height: 18, borderRadius: 9,
    backgroundColor: colors.labelAssistive,
    alignItems: 'center', justifyContent: 'center',
  },
});
