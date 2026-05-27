export type MedState = 'done' | 'now' | 'wait';

export interface MedSlot {
  id: string;
  time: string;
  label: string;
  state: MedState;
  items: string[];
  doseLogId?: number;
}

export interface ScheduleDay {
  date: string;
  totalCount: number;
  doneCount: number;
  slots: MedSlot[];
}
