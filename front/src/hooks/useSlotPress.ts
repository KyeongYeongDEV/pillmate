import { useCallback } from 'react';
import { Alert } from 'react-native';
import { useAppDispatch } from '@/store/hooks';
import { useCheckDoseMutation } from '@/store/slices/doseLogApi';
import { markDone, markWait } from '@/store/slices/doseStateSlice';

const CANCEL_CONFIRM_TITLE = '복약 취소';
const CANCEL_CONFIRM_MSG = '취소하시겠습니까?';
const TAKE_PARTIAL_FAIL_MSG = '일부 복약 기록에 실패했습니다. 다시 시도해 주세요.';
const CANCEL_PARTIAL_FAIL_MSG = '일부 복약 취소에 실패했습니다. 다시 시도해 주세요.';

export function useSlotPress() {
  const [checkDose] = useCheckDoseMutation();
  const dispatch = useAppDispatch();

  return useCallback((doseLogIds: number[], currentState: string) => {
    if (!doseLogIds.length) return;
    const primaryId = doseLogIds[0];

    if (currentState !== 'done') {
      if (doseLogIds.length === 1) {
        checkDose({ doseLogId: primaryId, action: 'TAKE' });
      } else {
        const promises = doseLogIds.map(id =>
          checkDose({ doseLogId: id, action: 'TAKE', skipOptimistic: true }).unwrap(),
        );
        Promise.allSettled(promises).then(results => {
          const failedCount = results.filter(r => r.status === 'rejected').length;
          if (failedCount > 0) {
            Alert.alert('복약 기록 실패', TAKE_PARTIAL_FAIL_MSG);
            results.forEach((r, i) => {
              if (r.status === 'fulfilled') dispatch(markDone({ doseLogId: doseLogIds[i] }));
            });
          } else {
            doseLogIds.forEach(id => dispatch(markDone({ doseLogId: id })));
          }
        });
      }
      return;
    }

    const fireCancel = () => {
      if (doseLogIds.length === 1) {
        checkDose({ doseLogId: primaryId, action: 'CANCEL' });
        return;
      }
      const promises = doseLogIds.map(id =>
        checkDose({ doseLogId: id, action: 'CANCEL', skipOptimistic: true }).unwrap(),
      );
      Promise.allSettled(promises).then(results => {
        const failedCount = results.filter(r => r.status === 'rejected').length;
        if (failedCount > 0) {
          Alert.alert('복약 취소 실패', CANCEL_PARTIAL_FAIL_MSG);
          results.forEach((r, i) => {
            if (r.status === 'fulfilled') dispatch(markWait({ doseLogId: doseLogIds[i] }));
          });
        } else {
          doseLogIds.forEach(id => dispatch(markWait({ doseLogId: id })));
        }
      });
    };

    Alert.alert(CANCEL_CONFIRM_TITLE, CANCEL_CONFIRM_MSG, [
      { text: '아니요', style: 'cancel' },
      { text: '예', style: 'destructive', onPress: fireCancel },
    ]);
  }, [checkDose, dispatch]);
}
