import { createApi } from '@reduxjs/toolkit/query/react';
import { createPillmateBaseQuery } from '@/lib/api/baseQuery';
import type { ApiEnvelope } from '@/lib/api/client';
import type { DrugDetail, DrugSearchResult } from '@/types/prescription';

export const drugApiSlice = createApi({
  reducerPath: 'drugApi',
  baseQuery: createPillmateBaseQuery(),
  keepUnusedDataFor: 60,
  endpoints: (build) => ({
    searchDrugs: build.query<DrugSearchResult[], string>({
      query: (q) => `/drugs/search?q=${encodeURIComponent(q)}`,
      transformResponse: (response: ApiEnvelope<DrugSearchResult[]>) => response?.data ?? [],
    }),
    getDrugDetail: build.query<DrugDetail, string>({
      query: (kdCode) => `/drugs/${encodeURIComponent(kdCode)}`,
      transformResponse: (response: ApiEnvelope<DrugDetail>) => {
        if (!response?.data) throw new Error('약 정보를 찾을 수 없습니다.');
        return response.data;
      },
    }),
  }),
});

export const {
  useLazySearchDrugsQuery,
  useSearchDrugsQuery,
  useGetDrugDetailQuery,
} = drugApiSlice;
