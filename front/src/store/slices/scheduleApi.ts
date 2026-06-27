import { createApi } from '@reduxjs/toolkit/query/react';
import { createPillmateBaseQuery } from '@/lib/api/baseQuery';
import type { ApiEnvelope } from '@/lib/api/client';
import type { ScheduleDay, SlotEditView, TimeOfDay } from '@/types/schedule';
import type { AdherenceLevel } from '@/utils/calendarUtils';

export interface MonthAdherenceDay {
  date: string;
  totalCount: number;
  takenCount: number;
  adherence: 'FULL' | 'PARTIAL' | 'MISS';
}

export interface MonthScheduleResponse {
  month: string;
  days: MonthAdherenceDay[];
}

export function toAdherenceMap(
  response: MonthScheduleResponse | null | undefined,
): Record<string, AdherenceLevel> {
  const map: Record<string, AdherenceLevel> = {};
  for (const day of response?.days ?? []) {
    map[day.date] = day.adherence.toLowerCase() as AdherenceLevel;
  }
  return map;
}

export const MOCK_SCHEDULE: ScheduleDay = {
  date: '2025-11-24',
  totalCount: 6,
  doneCount: 4,
  slots: [
    { id: 'morning', time: '08:00', label: '아침',    state: 'done', items: ['암로디핀 5mg', '메트포르민 500mg'],       doseLogId: 4, drugCount: 2, pillColors: ['#A8D4FF', '#FFAA6B'] },
    { id: 'noon',    time: '12:30', label: '점심',    state: 'now',  items: ['메트포르민 500mg', '글리메피리드 2mg'],   doseLogId: 5, drugCount: 3, pillColors: ['#FFB3C1', '#F5F5F5'] },
    { id: 'evening', time: '19:00', label: '저녁',    state: 'wait', items: ['아토르바스타틴 10mg'],                   doseLogId: 6, drugCount: 2, pillColors: ['#C4B5FD'] },
    { id: 'bedtime', time: '22:00', label: '취침 전', state: 'wait', items: ['오메가-3 1000mg'],                       doseLogId: 7, drugCount: 1, pillColors: ['#0066FF'] },
  ],
};

export const scheduleApiSlice = createApi({
  reducerPath: 'scheduleApi',
  baseQuery: createPillmateBaseQuery(),
  tagTypes: ['Schedule', 'MonthSchedule'],
  keepUnusedDataFor: 30,
  endpoints: (build) => ({
    getDaySchedule: build.query<ScheduleDay, string>({
      query: (date) => `/schedules/day?date=${date}`,
      transformResponse: (response: ApiEnvelope<ScheduleDay>) => response?.data ?? MOCK_SCHEDULE,
      providesTags: ['Schedule'],
    }),

    getMonthAdherence: build.query<Record<string, AdherenceLevel>, string>({
      query: (month) => `/schedules/month?month=${month}`,
      transformResponse: (response: ApiEnvelope<MonthScheduleResponse>) =>
        toAdherenceMap(response?.data),
      providesTags: ['MonthSchedule'],
    }),

    getPrescriptionSlots: build.query<SlotEditView[], number>({
      query: (prescriptionId) => `/schedules/prescriptions/${prescriptionId}/slots`,
      transformResponse: (response: ApiEnvelope<SlotEditView[]>) => response?.data ?? [],
      providesTags: (_r, _e, prescriptionId) => [{ type: 'Schedule', id: `presc-${prescriptionId}` }],
    }),

    updateScheduleTime: build.mutation<void, { scheduleId: number; customTime: string }>({
      query: ({ scheduleId, customTime }) => ({
        url: `/schedules/${scheduleId}`,
        method: 'PATCH',
        body: { customTime },
      }),
      invalidatesTags: (_r, _e, { scheduleId }) => [
        'Schedule',
        { type: 'Schedule', id: `presc-${scheduleId}` },
      ],
    }),

    addPrescriptionSlot: build.mutation<void, { prescriptionId: number; timeOfDay: TimeOfDay; customTime: string }>({
      query: ({ prescriptionId, timeOfDay, customTime }) => ({
        url: `/schedules/prescriptions/${prescriptionId}/slots`,
        method: 'POST',
        body: { timeOfDay, customTime },
      }),
      invalidatesTags: (_r, _e, { prescriptionId }) => [
        'Schedule',
        { type: 'Schedule', id: `presc-${prescriptionId}` },
      ],
    }),

    removePrescriptionSlot: build.mutation<void, { prescriptionId: number; timeOfDay: TimeOfDay }>({
      query: ({ prescriptionId, timeOfDay }) => ({
        url: `/schedules/prescriptions/${prescriptionId}/slots/${timeOfDay}/deactivate`,
        method: 'PATCH',
      }),
      invalidatesTags: (_r, _e, { prescriptionId }) => [
        'Schedule',
        { type: 'Schedule', id: `presc-${prescriptionId}` },
      ],
    }),

    updatePrescriptionPeriod: build.mutation<void, { prescriptionId: number; endDate: string }>({
      query: ({ prescriptionId, endDate }) => ({
        url: `/schedules/prescriptions/${prescriptionId}/period`,
        method: 'PATCH',
        body: { endDate },
      }),
      invalidatesTags: (_r, _e, { prescriptionId }) => [
        'Schedule',
        { type: 'Schedule', id: `presc-${prescriptionId}` },
      ],
    }),
  }),
});

export const {
  useGetDayScheduleQuery,
  useGetMonthAdherenceQuery,
  useGetPrescriptionSlotsQuery,
  useUpdateScheduleTimeMutation,
  useAddPrescriptionSlotMutation,
  useRemovePrescriptionSlotMutation,
  useUpdatePrescriptionPeriodMutation,
} = scheduleApiSlice;
