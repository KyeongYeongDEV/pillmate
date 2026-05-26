import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';
import { getToken } from '@/lib/auth/storage';
import { API_BASE_URL } from '@/lib/api/client';
import type {
  Candidate,
  OcrInput,
  RegisterPrescriptionResponse,
  UploadUrlInput,
  UploadUrlResponse,
} from '@/types/prescription';

export const prescriptionApiSlice = createApi({
  reducerPath: 'prescriptionApi',
  baseQuery: fetchBaseQuery({
    baseUrl: API_BASE_URL,
    prepareHeaders: async (headers) => {
      const token = await getToken();
      if (token) headers.set('Authorization', `Bearer ${token}`);
      // Phase 1 dummy user header
      headers.set('X-User-Id', '1');
      return headers;
    },
  }),
  tagTypes: ['Prescription', 'Candidate'],
  endpoints: (build) => ({
    issueUploadUrl: build.mutation<UploadUrlResponse, UploadUrlInput>({
      query: (body) => ({ url: '/prescriptions/upload-url', method: 'POST', body }),
    }),
    ocr: build.mutation<RegisterPrescriptionResponse, OcrInput>({
      query: (body) => ({ url: '/prescriptions/ocr', method: 'POST', body }),
      invalidatesTags: ['Prescription'],
    }),
    getCandidates: build.query<Candidate[], number>({
      query: (id) => `/prescriptions/${id}/candidates`,
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
  }),
});

export const {
  useIssueUploadUrlMutation,
  useOcrMutation,
  useGetCandidatesQuery,
  useResolveCandidateMutation,
} = prescriptionApiSlice;
