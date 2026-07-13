import { Middleware } from '@reduxjs/toolkit';
import { createTransform } from 'redux-persist';
import AsyncStorage from '@react-native-async-storage/async-storage';
import type { PersistConfig } from 'redux-persist';
import { scheduleApiSlice } from './slices/scheduleApi';
import { prescriptionApiSlice } from './slices/prescriptionApi';
import { caregroupApiSlice } from './slices/caregroupApi';
import { setSavedDateKst } from './slices/cacheMetaSlice';
import { getKstToday } from '@/utils/calendarUtils';

// 홈 표시에 필요한 3개 slice(+ 날짜 태그)만 whitelist — 검색 결과 등 대형 응답은 다른 slice 라 자동 제외.
// slice 자체는 whitelist 단위지만, 저장되는 쿼리는 fulfilledOnlyTransform 이 홈 3 엔드포인트로 추가 한정한다.
export const PERSIST_WHITELIST = [
  scheduleApiSlice.reducerPath,
  prescriptionApiSlice.reducerPath,
  caregroupApiSlice.reducerPath,
  'cacheMeta',
];

const RTK_QUERY_REDUCER_PATHS = [
  scheduleApiSlice.reducerPath,
  prescriptionApiSlice.reducerPath,
  caregroupApiSlice.reducerPath,
];

// 콜드 스타트 홈 렌더에 실제로 쓰이는 엔드포인트만 — 달력 브라우징(getMonthAdherence 등)은 익일 purge 전까지
// 불필요하게 디스크에 쌓이지 않도록 제외.
const HOME_ENDPOINT_NAMES = new Set(['getDaySchedule', 'getActiveWithInsights', 'getMyGroups']);

export const PERSIST_VERSION = 1;

// 네트워크 순단 중 종료→재실행 시 pending/rejected 쿼리가 그대로 저장되면, 이후 정상 재기동에도
// 에러 상태가 계속 표시되는 락업이 생긴다 — fulfilled 상태의 홈 3 엔드포인트만 저장/복원 대상으로 남긴다.
// mutations 는 항상 비움(등록/체크 등 일회성 동작이라 재실행 시 의미 없고, 약 정보 노출면만 늘어남).
export function keepFulfilledOnly(apiState: any) {
  if (!apiState) return apiState;
  const queries = apiState.queries
    ? Object.fromEntries(
        Object.entries(apiState.queries).filter(
          ([, q]: [string, any]) => q?.status === 'fulfilled' && HOME_ENDPOINT_NAMES.has(q?.endpointName),
        ),
      )
    : apiState.queries;
  const keptKeys = new Set(Object.keys(queries ?? {}));
  const subscriptions = apiState.subscriptions
    ? Object.fromEntries(Object.entries(apiState.subscriptions).filter(([key]) => keptKeys.has(key)))
    : apiState.subscriptions;
  return { ...apiState, queries, subscriptions, mutations: {} };
}

const fulfilledOnlyTransform = createTransform(
  (inboundState: any) => keepFulfilledOnly(inboundState), // 저장 시점 필터
  (outboundState: any) => keepFulfilledOnly(outboundState), // 이미 저장된 과거 데이터 대비 rehydrate 시점도 필터
  { whitelist: RTK_QUERY_REDUCER_PATHS },
);

export const persistConfig: PersistConfig<any> = {
  key: 'root',
  version: PERSIST_VERSION,
  storage: AsyncStorage,
  whitelist: PERSIST_WHITELIST,
  transforms: [fulfilledOnlyTransform],
  // redux-persist 는 migrate 를 rehydrate 마다 무조건 호출한다(버전 비교를 대신 해주지 않음) —
  // 버전이 같으면 그대로 통과, 다르면(스키마 변경 등) 폐기. 복잡한 마이그레이션은 no-overengineering 으로 보류.
  migrate: (state: any) => Promise.resolve(state?._persist?.version === PERSIST_VERSION ? state : ({} as any)),
};

// 자정을 넘겨 태그가 다른 전역 쿼리(그룹/인사이트) fulfilled 만으로 전진하면, 정작 오늘자 스케줄은
// 갱신 안 된 채 날짜가드를 통과해버릴 수 있다 — 태그 갱신은 "오늘 인자로 조회한 getDaySchedule 성공" 만으로 한정.
function isTodayScheduleFulfilled(action: unknown): boolean {
  if (!scheduleApiSlice.endpoints.getDaySchedule.matchFulfilled(action as any)) return false;
  const arg = (action as any).meta?.arg?.originalArgs;
  return arg === getKstToday();
}

// 캐시 저장 시점의 KST 날짜를 기록 — rehydrate 시 오늘과 비교해 어제 상태 오표시 방지.
export const cacheDateTagMiddleware: Middleware = (store) => (next) => (action) => {
  const result = next(action);
  if (isTodayScheduleFulfilled(action)) {
    const today = getKstToday();
    if (store.getState().cacheMeta?.savedDateKst !== today) {
      store.dispatch(setSavedDateKst(today));
    }
  }
  return result;
};

export function purgeStaleHomeCacheIfDateChanged(
  getState: () => { cacheMeta: { savedDateKst: string | null } },
  dispatch: (action: any) => unknown,
): void {
  const today = getKstToday();
  if (getState().cacheMeta.savedDateKst === today) return;
  dispatch(scheduleApiSlice.util.resetApiState());
  dispatch(prescriptionApiSlice.util.resetApiState());
  dispatch(caregroupApiSlice.util.resetApiState());
  dispatch(setSavedDateKst(today));
}
