import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';
import { API_BASE_URL } from '@/lib/api/client';
import type { Drug, DrugSearchResult } from '@/types/prescription';

export const drugApiSlice = createApi({
  reducerPath: 'drugApi',
  baseQuery: fetchBaseQuery({ baseUrl: API_BASE_URL }),
  endpoints: (build) => ({
    searchDrugs: build.query<DrugSearchResult[], string>({
      query: (q) => `/drugs/search?q=${encodeURIComponent(q)}`,
      keepUnusedDataFor: 60,
    }),
    getDrugDetail: build.query<Drug, string>({
      query: (kdCode) => `/drugs/${kdCode}`,
    }),
  }),
});

export const { useLazySearchDrugsQuery, useSearchDrugsQuery, useGetDrugDetailQuery } = drugApiSlice;
