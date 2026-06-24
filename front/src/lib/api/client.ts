import { Platform } from "react-native";

import { getToken, getCurrentUserId } from "@/lib/auth/storage";

// Android 에뮬레이터는 호스트 머신의 localhost 가 10.0.2.2.
function resolveBaseUrl(): string {
  const fromEnv = process.env.EXPO_PUBLIC_API_BASE_URL;
  if (fromEnv && fromEnv.length > 0) return fromEnv;
  return Platform.OS === "android"
    ? "http://10.0.2.2:8080/api/v1"
    : "http://localhost:8080/api/v1";
}

export const API_BASE_URL = resolveBaseUrl();

export type ApiEnvelope<T> = {
  data: T | null;
  message: string;
  timestamp: string;
  error: { code: string; message: string } | null;
};

export class ApiError extends Error {
  readonly code: string;
  readonly status: number;
  constructor(status: number, code: string, message: string) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

type RequestOptions = Omit<RequestInit, "body"> & {
  body?: unknown;
  auth?: boolean;
};

export async function apiFetch<T>(
  path: string,
  options: RequestOptions = {},
): Promise<T> {
  const { body, auth = true, headers, ...rest } = options;
  const finalHeaders = await buildHeaders(headers, auth);
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...rest,
    headers: finalHeaders,
    body: body == null ? undefined : JSON.stringify(body),
  });
  return parseEnvelope<T>(response);
}

async function buildHeaders(
  base: HeadersInit | undefined,
  withAuth: boolean,
): Promise<HeadersInit> {
  const userId = await getCurrentUserId();
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    Accept: "application/json",
    "X-User-Id": String(userId ?? 1),
    ...(base as Record<string, string> | undefined),
  };
  if (withAuth) {
    const token = await getToken();
    if (token) headers.Authorization = `Bearer ${token}`;
  }
  return headers;
}

async function parseEnvelope<T>(response: Response): Promise<T> {
  const envelope = (await response.json()) as ApiEnvelope<T>;
  if (!response.ok || envelope.error) {
    const code = envelope.error?.code ?? `HTTP_${response.status}`;
    const message = envelope.error?.message ?? response.statusText;
    throw new ApiError(response.status, code, message);
  }
  return envelope.data as T;
}
