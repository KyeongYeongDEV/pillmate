// 서버측 변화(타인 가입·초대 수락)는 내 앱의 invalidatesTags 로 감지되지 않으므로 재조회 트리거가 필요하다.
export const GROUP_DETAIL_REFRESH = {
  refetchOnMountOrArgChange: true,
  refetchOnFocus: true,
  pollingInterval: 7_000,
} as const;

export const GROUP_LIST_REFRESH = {
  refetchOnMountOrArgChange: true,
  refetchOnFocus: true,
  pollingInterval: 15_000,
} as const;

// 다른 API 슬라이스(prescriptionApi/scheduleApi)에서 약봉투를 등록해도 caregroupApi 의
// 그룹 캘린더 캐시는 invalidatesTags 로 감지되지 않는다(슬라이스가 다름) — 폴링으로 동기화.
export const GROUP_SCHEDULE_REFRESH = {
  refetchOnMountOrArgChange: true,
  refetchOnFocus: true,
  pollingInterval: 7_000,
} as const;
