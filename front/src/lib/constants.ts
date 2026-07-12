export const OCR_MIN_CONFIDENCE = 0.7;
export const RAG_MIN_FAITHFULNESS = 0.95;
export const MFDS_SOURCE = "식품의약품안전처";
export const DEBOUNCE_MS = 300;

export const AUTH_STORAGE_KEY = "pillmate.auth.jwt";

export const ACTIVITY_POLL_INTERVAL_MS = 10_000;

// 홈 AI 인사이트 — 복약중 처방전 목록을 10초마다 순환 표시
export const INSIGHT_ROTATE_INTERVAL_MS = 10_000;

// 복약 기간 입력 — 오늘 기준 1일부터, 의료 안전상 최대 365일 cap
export const MIN_DURATION_DAYS = 1;
export const MAX_DURATION_DAYS = 365;

// 주민번호(고유식별정보) 감지 시 등록 차단 — 개인정보보호법 리스크 회피
export const PII_BLOCK_ALERT_TITLE = '주민번호가 보여요';
export const PII_BLOCK_ALERT_MESSAGE = '가리고 다시 촬영해주세요.';

// 닉네임 변경 — 1~20자
export const NAME_MIN_LENGTH = 1;
export const NAME_MAX_LENGTH = 20;

// 최근 검색 — 화면 표시는 최신 6개만(저장은 8개, lib/search/recentSearches.ts)
export const RECENT_SEARCHES_DISPLAY_COUNT = 6;
