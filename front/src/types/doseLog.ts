export type DoseStatus = 'PENDING' | 'TAKEN' | 'SKIPPED' | 'MISSED' | 'DELAYED';

export type DoseAction = 'TAKE' | 'SKIP' | 'CANCEL';

export interface CheckDoseInput {
  doseLogId: number;
  action: DoseAction;
  skipReason?: string;
  skipOptimistic?: boolean;
}

export interface BulkCheckDoseInput {
  doseLogIds: number[];
  action: DoseAction;
  skipReason?: string;
}

export interface DoseLogResponse {
  id: number;
  scheduleId: number;
  patientId: number;
  scheduledAt: string;
  status: DoseStatus;
  checkedBy: number | null;
  checkedAt: string | null;
  skipReason: string | null;
}
