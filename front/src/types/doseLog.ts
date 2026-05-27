export type DoseStatus = 'PENDING' | 'TAKEN' | 'SKIPPED' | 'MISSED' | 'DELAYED';

export interface CheckDoseInput {
  doseLogId: number;
  action: 'TAKE' | 'SKIP';
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
