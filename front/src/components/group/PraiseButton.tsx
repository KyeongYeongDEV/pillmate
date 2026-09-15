import React, { useCallback, useEffect, useState } from 'react';
import { Pressable, Text, View, StyleSheet, ActivityIndicator } from 'react-native';
import { usePraiseActivityMutation } from '@/store/slices/activityApi';
import { scale, colors, space, radius } from '@/styles/tokens';

interface Props {
  activityFeedId: number;
  groupId: number;
  praisedByMe?: boolean;
}

// "복용 완료" 활동 옆에 붙는 칭찬 버튼. 서버가 (activity, praiser) 유니크로 중복 알림을
// 막아주므로 재탭해도 안전. praisedByMe 는 서버 진실源 — 어느 화면에서 조회해도
// "칭찬함" 이 일관되게 보이도록 초기 상태를 시딩한다 (탭 직후엔 로컬 상태로 즉시 반영).
export default function PraiseButton({ activityFeedId, groupId, praisedByMe }: Props) {
  const [praiseActivity, { isLoading }] = usePraiseActivityMutation();
  const [praised, setPraised] = useState(!!praisedByMe);

  useEffect(() => {
    if (praisedByMe) setPraised(true);
  }, [praisedByMe]);

  const handlePress = useCallback(async () => {
    if (praised || isLoading) return;
    try {
      await praiseActivity({ activityFeedId, groupId }).unwrap();
      setPraised(true);
    } catch {
      // 실패해도 조용히 무시 — 버튼이 그대로 남아 재시도 가능
    }
  }, [praised, isLoading, praiseActivity, activityFeedId, groupId]);

  if (praised) {
    return (
      <View style={styles.praisedWrap} accessibilityLabel="칭찬함">
        <Text style={styles.praisedText}>👏 칭찬함</Text>
      </View>
    );
  }

  return (
    <Pressable
      style={styles.btn}
      onPress={handlePress}
      disabled={isLoading}
      accessibilityRole="button"
      accessibilityLabel="칭찬하기"
    >
      {isLoading ? (
        <ActivityIndicator size="small" color={colors.primaryBase} />
      ) : (
        <Text style={styles.btnText}>👏 칭찬하기</Text>
      )}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  btn: {
    paddingHorizontal: space.s10, paddingVertical: space.s4,
    borderRadius: radius.full, backgroundColor: colors.fillNormal,
    alignSelf: 'flex-start',
  },
  btnText: { fontSize: scale(12), fontWeight: '700', color: colors.primaryBase },
  praisedWrap: {
    paddingHorizontal: space.s10, paddingVertical: space.s4,
    alignSelf: 'flex-start',
  },
  praisedText: { fontSize: scale(12), fontWeight: '600', color: colors.labelAlternative },
});
