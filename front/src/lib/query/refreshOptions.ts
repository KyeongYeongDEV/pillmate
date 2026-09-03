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
