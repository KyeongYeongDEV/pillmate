import { useCallback } from 'react';
import { Alert } from 'react-native';
import { useAppSelector } from '@/store/hooks';
import { useCheckDoseMutation } from '@/store/slices/doseLogApi';
import { selectIsLocked } from '@/store/slices/doseStateSlice';
import type { RootState } from '@/store';

const LOCK_ALERT_TITLE = '취소 불가';
const LOCK_ALERT_MSG = '복약 완료는 60초 후 취소할 수 없습니다.';

export function useSlotPress() {
  const doseStateMap = useAppSelector((state: RootState) => state.doseState);
  const [checkDose] = useCheckDoseMutation();

  return useCallback((doseLogId: number | undefined, currentState: string) => {
    if (!doseLogId) return;

    if (currentState === 'done' && selectIsLocked(doseStateMap, doseLogId, Date.now())) {
      Alert.alert(LOCK_ALERT_TITLE, LOCK_ALERT_MSG);
      return;
    }

    const action = currentState === 'done' ? 'SKIP' : 'TAKE';
    checkDose({ doseLogId, action });
  }, [doseStateMap, checkDose]);
}
