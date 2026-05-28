import { fetchBaseQuery } from '@reduxjs/toolkit/query/react';
import { API_BASE_URL } from './client';
import { getToken, getCurrentUserId } from '@/lib/auth/storage';

interface BaseQueryConfig {
  baseUrl?: string;
}

export const createPillmateBaseQuery = (config?: BaseQueryConfig) =>
  fetchBaseQuery({
    baseUrl: config?.baseUrl ?? API_BASE_URL,
    prepareHeaders: async (headers) => {
      const token = await getToken();
      if (token) headers.set('Authorization', `Bearer ${token}`);
      const userId = await getCurrentUserId();
      if (userId != null) headers.set('X-User-Id', String(userId));
      return headers;
    },
  });
