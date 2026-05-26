import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';
import type { Drug, DrugSearchResult } from '@/types/prescription';

// Phase 1 mock data — swap queryFn for real fetch in Phase 2 once backend is ready
const MOCK_DRUGS: DrugSearchResult[] = [
  { id: 1,  kdCode: '670500700', name: '암로디핀정 5mg',          company: '한미약품',   imageUrl: null },
  { id: 2,  kdCode: '670500701', name: '암로디핀베실산염정 5mg',  company: '대웅제약',   imageUrl: null },
  { id: 3,  kdCode: '670500702', name: '암로핀정 10mg',           company: '종근당',     imageUrl: null },
  { id: 4,  kdCode: '670500703', name: '메트포르민정 500mg',      company: '동아제약',   imageUrl: null },
  { id: 5,  kdCode: '670500704', name: '글리메피리드정 2mg',      company: '한림제약',   imageUrl: null },
  { id: 6,  kdCode: '670500705', name: '아토르바스타틴정 10mg',   company: '한국화이자', imageUrl: null },
  { id: 7,  kdCode: '670500706', name: '오메가-3 지방산 1000mg',  company: '일동제약',   imageUrl: null },
  { id: 8,  kdCode: '670500707', name: '노바스크정 5mg',          company: '한국화이자', imageUrl: null },
  { id: 9,  kdCode: '670500708', name: '카나브정 30mg',           company: '보령제약',   imageUrl: null },
  { id: 10, kdCode: '670500709', name: '에소메프라졸캡슐 20mg',   company: '동아에스티', imageUrl: null },
];

export const drugApiSlice = createApi({
  reducerPath: 'drugApi',
  baseQuery: fetchBaseQuery({ baseUrl: '' }),
  keepUnusedDataFor: 60,
  endpoints: (build) => ({
    searchDrugs: build.query<DrugSearchResult[], string>({
      queryFn: async (q) => {
        if (!q.trim()) return { data: [] };
        const lower = q.toLowerCase();
        const data = MOCK_DRUGS.filter(d =>
          d.name.toLowerCase().includes(lower) ||
          (d.company?.toLowerCase().includes(lower) ?? false),
        );
        return { data };
      },
    }),
    getDrugDetail: build.query<Drug, string>({
      queryFn: async (kdCode) => {
        const found = MOCK_DRUGS.find(d => d.kdCode === kdCode);
        if (!found) return { error: { status: 404, data: 'Not found' } };
        const drug: Drug = { ...found, ingredient: null, efficacy: null, dosage: null };
        return { data: drug };
      },
    }),
  }),
});

export const { useLazySearchDrugsQuery, useSearchDrugsQuery, useGetDrugDetailQuery } = drugApiSlice;
