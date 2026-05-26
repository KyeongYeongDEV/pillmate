import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';
import type { ChatRequest, ChatResponse } from '@/types/chat';

const MOCK_RESPONSE: ChatResponse = {
  id: 'mock-ai-1',
  hasWarning: true,
  warningText: '일부 감기약은 주의가 필요해요',
  content:
    '암로디핀은 일반 감기약과 대체로 함께 복용 가능하지만, 슈도에페드린 성분이 포함된 감기약은 혈압을 올릴 수 있어 피해야 합니다.',
  sources: [
    { organization: '식약처 의약품안전나라', document: '암로디핀정 병용주의' },
    { organization: '대한고혈압학회', document: '고혈압 환자의 감기약 복용 지침 2024' },
  ],
};

export const chatApiSlice = createApi({
  reducerPath: 'chatApi',
  baseQuery: fetchBaseQuery({ baseUrl: '' }),
  endpoints: (build) => ({
    sendMessage: build.mutation<ChatResponse, ChatRequest>({
      // Phase 2: replace queryFn with query: (body) => ({ url: '/chat', method: 'POST', body })
      queryFn: async () => ({ data: MOCK_RESPONSE }),
    }),
  }),
});

export const { useSendMessageMutation } = chatApiSlice;
