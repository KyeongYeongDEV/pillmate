import React, { memo } from 'react';
import {
  Modal, View, Text, Pressable, StyleSheet, KeyboardAvoidingView, Platform,
} from 'react-native';
import { scale, colors, typography, space, radius } from '@/styles/tokens';
import DrugSearchAutocomplete from './DrugSearchAutocomplete';
import type { DrugSearchResult } from '@/types/prescription';

interface Props {
  visible: boolean;
  title?: string;
  onClose: () => void;
  onSelect: (drug: DrugSearchResult) => void;
}

function DrugSearchModal({ visible, title = '약 검색', onClose, onSelect }: Props) {
  const handleSelect = (drug: DrugSearchResult) => {
    onSelect(drug);
    onClose();
  };

  return (
    <Modal
      visible={visible}
      animationType="slide"
      transparent
      onRequestClose={onClose}
      accessibilityViewIsModal
    >
      <Pressable style={styles.backdrop} onPress={onClose} accessibilityLabel="닫기" />
      <KeyboardAvoidingView
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
        style={styles.sheet}
      >
        {/* 드래그 핸들 */}
        <View style={styles.handle} />

        {/* 헤더 */}
        <View style={styles.header}>
          <Text style={styles.title}>{title}</Text>
          <Pressable
            onPress={onClose}
            style={styles.closeBtn}
            accessibilityLabel="닫기"
            accessibilityRole="button"
          >
            <Text style={styles.closeTxt}>✕</Text>
          </Pressable>
        </View>

        {/* 안내 문구 */}
        <Text style={styles.hint}>
          약 이름으로 검색 후 선택해 주세요. 출처: 식품의약품안전처
        </Text>

        {/* 검색 컴포넌트 */}
        <View style={styles.searchArea}>
          <DrugSearchAutocomplete onSelect={handleSelect} />
        </View>
      </KeyboardAvoidingView>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.4)',
  },
  sheet: {
    backgroundColor: colors.bgNormal,
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
    paddingHorizontal: space.s16,
    paddingBottom: space.s32,
    maxHeight: '70%',
  },
  handle: {
    width: scale(40), height: scale(4), borderRadius: scale(2),
    backgroundColor: colors.lineSolidNorm,
    alignSelf: 'center',
    marginTop: space.s12,
    marginBottom: space.s8,
  },
  header: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    paddingVertical: space.s12,
  },
  title: { ...typography.headline2, color: colors.labelNormal },
  closeBtn: {
    width: scale(32), height: scale(32), borderRadius: scale(16),
    backgroundColor: colors.fillNormal,
    alignItems: 'center', justifyContent: 'center',
  },
  closeTxt: { fontSize: scale(14), color: colors.labelAlternative },
  hint: {
    ...typography.caption1, color: colors.labelAlternative,
    marginBottom: space.s12,
  },
  searchArea: { flex: 1 },
});

export default memo(DrugSearchModal);
