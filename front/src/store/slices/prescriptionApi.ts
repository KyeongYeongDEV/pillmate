import { createApi } from '@reduxjs/toolkit/query/react';
import { createPillmateBaseQuery } from '@/lib/api/baseQuery';
import type { ApiEnvelope } from '@/lib/api/client';
import type {
  Candidate,
  LatestPrescriptionWithInsight,
  OcrInput,
  OcrExtractResponse,
  PrescriptionDetailView,
  PrescriptionSummary,
  RegisterPrescriptionResponse,
  RegisterPrescriptionInput,
  UploadUrlInput,
  UploadUrlResponse,
} from '@/types/prescription';
import type { AliasLog } from '@/hooks/usePrescriptionReview';
import { scheduleApiSlice } from './scheduleApi';
import { doseLogApiSlice } from './doseLogApi';

// 처방전 등록/OCR 성공 시 홈 '오늘의 복약'(Schedule/DoseLog/Activity)·월간 스케줄을 cross-slice 무효화
function invalidateScheduleAfterRegister(dispatch: (action: any) => void) {
  dispatch(scheduleApiSlice.util.invalidateTags(['Schedule', 'MonthSchedule']));
  dispatch(doseLogApiSlice.util.invalidateTags(['Schedule', 'DoseLog', 'Activity']));
}

async function onRegisterFulfilled(
  _arg: unknown,
  { dispatch, queryFulfilled }: { dispatch: (action: any) => void; queryFulfilled: Promise<unknown> },
) {
  try {
    await queryFulfilled;
    invalidateScheduleAfterRegister(dispatch);
  } catch {
    /* 등록 실패 시 invalidate 스킵 */
  }
}

export const prescriptionApiSlice = createApi({
  reducerPath: 'prescriptionApi',
  baseQuery: createPillmateBaseQuery(),
  tagTypes: ['Prescription', 'Candidate'],
  endpoints: (build) => ({
    getPrescriptions: build.query<PrescriptionSummary[], void>({
      query: () => '/prescriptions',
      transformResponse: (response: ApiEnvelope<PrescriptionSummary[]>) => response?.data ?? [],
      providesTags: ['Prescription'],
    }),
    getPrescriptionDetail: build.query<PrescriptionDetailView | null, number>({
      query: (id) => `/prescriptions/${id}`,
      transformResponse: (response: ApiEnvelope<PrescriptionDetailView>) => response?.data ?? null,
      providesTags: (_r, _e, id) => [{ type: 'Prescription', id }],
    }),
    getLatestWithInsight: build.query<LatestPrescriptionWithInsight | null, void>({
      query: () => '/prescriptions/latest-with-insight',
      transformResponse: (response: ApiEnvelope<LatestPrescriptionWithInsight>) => response?.data ?? null,
      providesTags: ['Prescription'],
    }),
    getActiveWithInsights: build.query<LatestPrescriptionWithInsight[], void>({
      query: () => '/prescriptions/active-with-insights',
      transformResponse: (response: ApiEnvelope<LatestPrescriptionWithInsight[]>) => response?.data ?? [],
      providesTags: ['Prescription'],
    }),
    issueUploadUrl: build.mutation<UploadUrlResponse, UploadUrlInput>({
      query: (body) => ({ url: '/prescriptions/upload-url', method: 'POST', body }),
    }),
    ocr: build.mutation<RegisterPrescriptionResponse, OcrInput>({
      query: (body) => ({ url: '/prescriptions/ocr', method: 'POST', body }),
      invalidatesTags: ['Prescription'],
      onQueryStarted: onRegisterFulfilled,
    }),
    getCandidates: build.query<Candidate[], number>({
      query: (id) => `/prescriptions/${id}/candidates`,
      transformResponse: (response: ApiEnvelope<Candidate[]>) => response?.data ?? [],
      providesTags: (_r, _e, id) => [{ type: 'Candidate', id }],
    }),
    resolveCandidate: build.mutation<void, { prescriptionId: number; candidateId: number; selectedDrugId: number }>({
      query: ({ prescriptionId, candidateId, selectedDrugId }) => ({
        url: `/prescriptions/${prescriptionId}/candidates/${candidateId}/resolve`,
        method: 'PUT',
        body: { selectedDrugId },
      }),
      invalidatesTags: (_r, _e, { prescriptionId }) => [
        { type: 'Candidate', id: prescriptionId },
        'Prescription',
      ],
    }),
    ocrExtract: build.mutation<OcrExtractResponse, { imageKey: string; prescribedAt: string }>({
      query: (body) => ({ url: '/prescriptions/ocr/extract', method: 'POST', body }),
    }),
    registerPrescription: build.mutation<RegisterPrescriptionResponse, RegisterPrescriptionInput>({
      query: (body) => ({ url: '/prescriptions', method: 'POST', body }),
      transformResponse: (response: ApiEnvelope<RegisterPrescriptionResponse>) =>
        response?.data ?? { prescriptionId: 0, ocrStatus: 'MANUAL' as const, items: [], warnings: [] },
      invalidatesTags: ['Prescription'],
      onQueryStarted: onRegisterFulfilled,
    }),
    updatePrescription: build.mutation<void, { id: number; label?: string | null; memo?: string | null }>({
      query: ({ id, ...body }) => ({
        url: `/prescriptions/${id}`,
        method: 'PATCH',
        body,
      }),
      invalidatesTags: (_r, _e, { id }) => [{ type: 'Prescription', id }, 'Prescription'],
    }),
    deletePrescription: build.mutation<void, number>({
      query: (id) => ({ url: `/prescriptions/${id}`, method: 'DELETE' }),
      invalidatesTags: (_r, _e, id) => [{ type: 'Prescription', id }, 'Prescription'],
    }),

    // MVP: 로깅만 — admin review 후 prod 반영 (Phase 2 학습)
    logAlias: build.mutation<void, AliasLog>({
      query: (body) => ({
        url: '/drugs/aliases',
        method: 'POST',
        body: { nameRaw: body.nameRaw, itemSeq: body.itemSeq },
      }),
    }),
  }),
});

export const {
  useGetPrescriptionsQuery,
  useGetPrescriptionDetailQuery,
  useGetLatestWithInsightQuery,
  useGetActiveWithInsightsQuery,
  useIssueUploadUrlMutation,
  useOcrMutation,
  useOcrExtractMutation,
  useRegisterPrescriptionMutation,
  useGetCandidatesQuery,
  useResolveCandidateMutation,
  useUpdatePrescriptionMutation,
  useDeletePrescriptionMutation,
  useLogAliasMutation,
} = prescriptionApiSlice;
