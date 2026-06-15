import { createApi } from '@reduxjs/toolkit/query/react';
import { createPillmateBaseQuery } from '@/lib/api/baseQuery';
import type { ApiEnvelope } from '@/lib/api/client';
import type {
  Candidate,
  OcrInput,
  PrescriptionDetailView,
  PrescriptionSummary,
  RegisterPrescriptionResponse,
  UploadUrlInput,
  UploadUrlResponse,
} from '@/types/prescription';
import type { AliasLog } from '@/hooks/usePrescriptionReview';

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
    issueUploadUrl: build.mutation<UploadUrlResponse, UploadUrlInput>({
      query: (body) => ({ url: '/prescriptions/upload-url', method: 'POST', body }),
    }),
    ocr: build.mutation<RegisterPrescriptionResponse, OcrInput>({
      query: (body) => ({ url: '/prescriptions/ocr', method: 'POST', body }),
      invalidatesTags: ['Prescription'],
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
  useIssueUploadUrlMutation,
  useOcrMutation,
  useGetCandidatesQuery,
  useResolveCandidateMutation,
  useLogAliasMutation,
} = prescriptionApiSlice;
