import type { OcrStatus } from '@/types/prescription';
import { colors } from '@/styles/tokens';

interface OcrStatusChip {
  label: string;
  color: string;
}

const CHIPS: Record<OcrStatus, OcrStatusChip> = {
  PENDING: { label: '대기 중', color: colors.labelAssistive },
  PROCESSING: { label: '인식 중', color: colors.primaryBase },
  DONE: { label: '완료', color: colors.statusPositive },
  FAILED: { label: '실패', color: colors.statusNegative },
  MANUAL: { label: '확인 필요', color: colors.statusCautionary },
};

export function ocrStatusChip(status: OcrStatus): OcrStatusChip {
  return CHIPS[status] ?? CHIPS.PENDING;
}
