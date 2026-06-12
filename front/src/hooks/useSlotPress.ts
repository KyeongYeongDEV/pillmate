import { useCallback } from 'react';
import { Alert } from 'react-native';
import { useAppSelector } from '@/store/hooks';
import { useCheckDoseMutation } from '@/store/slices/doseLogApi';
import { selectIsLocked } from '@/store/slices/doseStateSlice';
import type { RootState } from '@/store';

const CANCEL_CONFIRM_TITLE = '복약 취소';
const CANCEL_CONFIRM_MSG = '취소하시겠습니까?';

export function useSlotPress() {
  const doseStateMap = useAppSelector((state: RootState) => state.doseState);
  const [checkDose] = useCheckDoseMutation();

  return useCallback((doseLogId: number | undefined, currentState: string) => {
    if (!doseLogId) return;

    if (currentState !== 'done') {
      checkDose({ doseLogId, action: 'TAKE' });
      return;
    }

    const fireCancel = () => checkDose({ doseLogId, action: 'CANCEL' });

    if (selectIsLocked(doseStateMap, doseLogId, Date.now())) {
      Alert.alert(CANCEL_CONFIRM_TITLE, CANCEL_CONFIRM_MSG, [
        { text: '아니요', style: 'cancel' },
        { text: '예', onPress: fireCancel },
      ]);
      return;
    }

    fireCancel();
  }, [doseStateMap, checkDose]);
}
