import { createApi } from '@reduxjs/toolkit/query/react';
import { createPillmateBaseQuery } from '@/lib/api/baseQuery';
import type { BulkCheckDoseInput, CheckDoseInput, DoseLogResponse } from '@/types/doseLog';
import { markDone, markWait, markDoneNoLock } from './doseStateSlice';
import { scheduleApiSlice } from './scheduleApi';
import { caregroupApiSlice } from './caregroupApi';

// 복약 체크 성공 후 cross-slice cache 무효화 (slice 별 namespace 독립이라 명시 필요)
export function invalidateAfterDoseMutation(dispatch: (action: any) => void) {
  dispatch(scheduleApiSlice.util.invalidateTags(['MonthSchedule']));
  dispatch(caregroupApiSlice.util.invalidateTags(['Group']));
}

export const doseLogApiSlice = createApi({
  reducerPath: 'doseLogApi',
  baseQuery: createPillmateBaseQuery(),
  tagTypes: ['Schedule', 'DoseLog', 'Activity'],
  endpoints: (build) => ({
    checkDose: build.mutation<DoseLogResponse, CheckDoseInput>({
      query: ({ skipOptimistic: _skipOptimistic, ...body }) => ({
        url: '/dose-logs/check',
        method: 'PATCH',
        body,
      }),
      invalidatesTags: ['Schedule', 'DoseLog', 'Activity'],
      async onQueryStarted({ doseLogId, action, skipOptimistic }, { dispatch, queryFulfilled }) {
        if (!skipOptimistic) {
          dispatch(action === 'TAKE' ? markDone({ doseLogId }) : markWait({ doseLogId }));
        }
        try {
          await queryFulfilled;
          invalidateAfterDoseMutation(dispatch);
        } catch {
          // Revert on network failure; markDoneNoLock avoids restarting the 60s timer
          if (!skipOptimistic) {
            dispatch(action === 'TAKE' ? markWait({ doseLogId }) : markDoneNoLock({ doseLogId }));
          }
        }
      },
    }),

    // 슬롯 단위 일괄 체크 — 단일 트랜잭션 + ActivityFeed 1회 (BE 원자 처리)
    bulkCheckDose: build.mutation<DoseLogResponse[], BulkCheckDoseInput>({
      query: (body) => ({
        url: '/dose-logs/bulk-check',
        method: 'PATCH',
        body,
      }),
      invalidatesTags: ['Schedule', 'DoseLog', 'Activity'],
      async onQueryStarted({ doseLogIds, action }, { dispatch, queryFulfilled }) {
        doseLogIds.forEach(doseLogId =>
          dispatch(action === 'TAKE' ? markDone({ doseLogId }) : markWait({ doseLogId })),
        );
        try {
          await queryFulfilled;
          invalidateAfterDoseMutation(dispatch);
        } catch {
          doseLogIds.forEach(doseLogId =>
            dispatch(action === 'TAKE' ? markWait({ doseLogId }) : markDoneNoLock({ doseLogId })),
          );
        }
      },
    }),
  }),
});

export const { useCheckDoseMutation, useBulkCheckDoseMutation } = doseLogApiSlice;
