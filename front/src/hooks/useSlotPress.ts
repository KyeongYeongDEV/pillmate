import { useCallback } from 'react';
import { Alert } from 'react-native';
import { useCheckDoseMutation, useBulkCheckDoseMutation } from '@/store/slices/doseLogApi';
import type { DoseAction } from '@/types/doseLog';

const CANCEL_CONFIRM_TITLE = '복약 취소';
const CANCEL_CONFIRM_MSG = '취소하시겠습니까?';

export function useSlotPress() {
  const [checkDose] = useCheckDoseMutation();
  const [bulkCheckDose] = useBulkCheckDoseMutation();

  return useCallback((doseLogIds: number[], currentState: string) => {
    if (!doseLogIds.length) return;

    const fire = (action: DoseAction) => {
      if (doseLogIds.length === 1) {
        checkDose({ doseLogId: doseLogIds[0], action });
      } else {
        bulkCheckDose({ doseLogIds, action });
      }
    };

    if (currentState !== 'done') {
      fire('TAKE');
      return;
    }

    Alert.alert(CANCEL_CONFIRM_TITLE, CANCEL_CONFIRM_MSG, [
      { text: '아니요', style: 'cancel' },
      { text: '예', style: 'destructive', onPress: () => fire('CANCEL') },
    ]);
  }, [checkDose, bulkCheckDose]);
}
