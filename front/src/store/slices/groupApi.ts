import { createApi, fakeBaseQuery } from '@reduxjs/toolkit/query/react';

export interface PatientOption {
  userId: number;
  name: string;
  isSelf: boolean;
}

const MOCK_PATIENTS: PatientOption[] = [
  { userId: 1, name: '나 (본인)', isSelf: true },
  { userId: 2, name: '박순자 (할머니)', isSelf: false },
];

export const groupApi = createApi({
  reducerPath: 'groupApi',
  baseQuery: fakeBaseQuery(),
  endpoints: (build) => ({
    getMyPatients: build.query<PatientOption[], void>({
      // Phase 2: replace with real endpoint GET /api/v1/groups/my/patients
      queryFn: () => ({ data: MOCK_PATIENTS }),
      keepUnusedDataFor: 300,
    }),
  }),
});

export const { useGetMyPatientsQuery } = groupApi;
