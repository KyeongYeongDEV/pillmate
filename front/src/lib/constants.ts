export const OCR_MIN_CONFIDENCE = 0.7;
export const RAG_MIN_FAITHFULNESS = 0.95;
export const MFDS_SOURCE = "식품의약품안전처";
export const DEBOUNCE_MS = 300;

export const AUTH_STORAGE_KEY = "pillmate.auth.jwt";

export const ACTIVITY_POLL_INTERVAL_MS = 30_000;

// 복약 기간 입력 — 오늘 기준 1일부터, 의료 안전상 최대 365일 cap
export const MIN_DURATION_DAYS = 1;
export const MAX_DURATION_DAYS = 365;
