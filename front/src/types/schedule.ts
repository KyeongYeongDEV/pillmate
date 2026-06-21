export type MedState = 'done' | 'now' | 'wait';

export interface MedSlot {
  id: string;
  time: string;
  label: string;
  state: MedState;
  items: string[];
  doseLogId?: number;
  doseLogIds?: number[];
  drugCount?: number;
  pillColors?: string[];
  prescriptionId?: number;
  prescriptionName?: string;
  customTime?: string;
}

export interface ScheduleDay {
  date: string;
  totalCount: number;
  doneCount: number;
  slots: MedSlot[];
}

export type TimeOfDay = 'MORNING' | 'NOON' | 'EVENING';

export interface SlotEditView {
  scheduleId: number;
  timeOfDay: TimeOfDay;
  time: string;
  endDate: string;
  editable: boolean;
}
