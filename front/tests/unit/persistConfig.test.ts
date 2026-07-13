jest.mock('@/utils/calendarUtils', () => ({
  getKstToday: jest.fn(),
}));

import { getKstToday } from '@/utils/calendarUtils';
import { scheduleApiSlice } from '@/store/slices/scheduleApi';
import { prescriptionApiSlice } from '@/store/slices/prescriptionApi';
import { caregroupApiSlice } from '@/store/slices/caregroupApi';
import { setSavedDateKst } from '@/store/slices/cacheMetaSlice';
import {
  purgeStaleHomeCacheIfDateChanged, keepFulfilledOnly, persistConfig, PERSIST_VERSION,
  cacheDateTagMiddleware,
} from '@/store/persistConfig';

describe('purgeStaleHomeCacheIfDateChanged — 콜드 스타트 날짜 가드', () => {
  beforeEach(() => {
    (getKstToday as jest.Mock).mockReset();
  });

  it('저장된 날짜 == 오늘 이면 아무것도 하지 않음 (stale-while-revalidate 유지)', () => {
    (getKstToday as jest.Mock).mockReturnValue('2026-07-13');
    const dispatch = jest.fn();
    const getState = () => ({ cacheMeta: { savedDateKst: '2026-07-13' } });

    purgeStaleHomeCacheIfDateChanged(getState, dispatch);

    expect(dispatch).not.toHaveBeenCalled();
  });

  it('저장된 날짜 != 오늘 이면 3개 api 캐시 reset + 날짜 갱신 (어제 복약 상태 오표시 방지)', () => {
    (getKstToday as jest.Mock).mockReturnValue('2026-07-14');
    const dispatch = jest.fn();
    const getState = () => ({ cacheMeta: { savedDateKst: '2026-07-13' } });

    purgeStaleHomeCacheIfDateChanged(getState, dispatch);

    expect(dispatch).toHaveBeenCalledWith(scheduleApiSlice.util.resetApiState());
    expect(dispatch).toHaveBeenCalledWith(prescriptionApiSlice.util.resetApiState());
    expect(dispatch).toHaveBeenCalledWith(caregroupApiSlice.util.resetApiState());
    expect(dispatch).toHaveBeenCalledWith(setSavedDateKst('2026-07-14'));
  });

  it('저장된 날짜가 null(첫 실행) 이면 오늘 날짜로만 기록 (캐시 자체가 없으므로 reset 은 no-op 이나 동일 경로)', () => {
    (getKstToday as jest.Mock).mockReturnValue('2026-07-13');
    const dispatch = jest.fn();
    const getState = () => ({ cacheMeta: { savedDateKst: null } });

    purgeStaleHomeCacheIfDateChanged(getState, dispatch);

    expect(dispatch).toHaveBeenCalledWith(setSavedDateKst('2026-07-13'));
  });
});

describe('keepFulfilledOnly — 네트워크 순단 락업 방지 + 홈 3 엔드포인트 한정', () => {
  it('rejected 쿼리는 저장 대상에서 제외된다', () => {
    const apiState = {
      queries: {
        'getDaySchedule("2026-07-13")': {
          status: 'rejected', endpointName: 'getDaySchedule', error: { status: 'FETCH_ERROR' },
        },
      },
      subscriptions: { 'getDaySchedule("2026-07-13")': { abc: {} } },
      mutations: {},
      provided: {},
      config: {},
    };

    const result = keepFulfilledOnly(apiState);

    expect(result.queries).toEqual({});
    expect(result.subscriptions).toEqual({});
  });

  it('pending 쿼리도 저장 대상에서 제외된다', () => {
    const apiState = {
      queries: { 'getMyGroups(undefined)': { status: 'pending', endpointName: 'getMyGroups' } },
      subscriptions: {},
      mutations: {},
    };

    const result = keepFulfilledOnly(apiState);

    expect(result.queries).toEqual({});
  });

  it('fulfilled + 홈 엔드포인트는 그대로 유지된다 (구독 정보도 함께 유지)', () => {
    const apiState = {
      queries: {
        'getMyGroups(undefined)': { status: 'fulfilled', endpointName: 'getMyGroups', data: [{ groupId: 1 }] },
      },
      subscriptions: { 'getMyGroups(undefined)': { req1: {} } },
      mutations: {},
    };

    const result = keepFulfilledOnly(apiState);

    expect(result.queries).toEqual(apiState.queries);
    expect(result.subscriptions).toEqual(apiState.subscriptions);
  });

  it('fulfilled 라도 홈 3 엔드포인트가 아니면(getMonthAdherence 등) 저장 제외 — 달력 브라우징 누적 방지', () => {
    const apiState = {
      queries: {
        'getDaySchedule("2026-07-13")': { status: 'fulfilled', endpointName: 'getDaySchedule', data: { slots: [] } },
        'getMonthAdherence("2026-07")': { status: 'fulfilled', endpointName: 'getMonthAdherence', data: {} },
      },
      subscriptions: {
        'getDaySchedule("2026-07-13")': { req1: {} },
        'getMonthAdherence("2026-07")': { req2: {} },
      },
      mutations: {},
    };

    const result = keepFulfilledOnly(apiState);

    expect(Object.keys(result.queries)).toEqual(['getDaySchedule("2026-07-13")']);
    expect(Object.keys(result.subscriptions)).toEqual(['getDaySchedule("2026-07-13")']);
  });

  it('mutations 는 fulfilled 여부와 무관하게 항상 비운다', () => {
    const apiState = {
      queries: {},
      subscriptions: {},
      mutations: { 'someFixedCacheKey': { status: 'fulfilled', data: { ok: true } } },
    };

    const result = keepFulfilledOnly(apiState);

    expect(result.mutations).toEqual({});
  });

  it('queries 없는 state 도 크래시 없이 통과(mutations 만 비움)', () => {
    const state = { savedDateKst: '2026-07-13' };
    expect(() => keepFulfilledOnly(state)).not.toThrow();
  });
});

describe('persistConfig.migrate — P0: rehydrate 마다 무조건 폐기되던 버그 픽스', () => {
  it('버전이 같으면 저장된 state 를 그대로 통과시킨다 (캐시 유지)', async () => {
    const stored = { cacheMeta: '{"savedDateKst":"2026-07-13"}', _persist: { version: PERSIST_VERSION, rehydrated: true } };
    const migrated = await persistConfig.migrate!(stored as any, PERSIST_VERSION);
    expect(migrated).toBe(stored);
  });

  it('버전이 다르면 폐기한다 (스키마 변경 대비)', async () => {
    const stored = { cacheMeta: '{"savedDateKst":"2026-07-13"}', _persist: { version: 0, rehydrated: true } };
    const migrated = await persistConfig.migrate!(stored as any, PERSIST_VERSION);
    expect(migrated).toEqual({});
  });

  it('_persist 자체가 없으면(비정상 상태) 폐기한다', async () => {
    const stored = { cacheMeta: '{"savedDateKst":"2026-07-13"}' };
    const migrated = await persistConfig.migrate!(stored as any, PERSIST_VERSION);
    expect(migrated).toEqual({});
  });
});

describe('cacheDateTagMiddleware — 날짜 태그 소스를 오늘자 getDaySchedule fulfilled 로 한정', () => {
  function runMiddleware(action: any, savedDateKst: string | null) {
    const dispatch = jest.fn();
    const getState = jest.fn(() => ({ cacheMeta: { savedDateKst } }));
    const store = { dispatch, getState };
    const next = jest.fn((a) => a);
    cacheDateTagMiddleware(store as any)(next)(action);
    return dispatch;
  }

  beforeEach(() => {
    (getKstToday as jest.Mock).mockReset();
  });

  it('오늘 인자의 getDaySchedule fulfilled → 태그 갱신', () => {
    (getKstToday as jest.Mock).mockReturnValue('2026-07-14');
    const fulfilledAction = {
      type: 'scheduleApi/executeQuery/fulfilled',
      meta: { arg: { endpointName: 'getDaySchedule', originalArgs: '2026-07-14' } },
    };
    // matchFulfilled 는 RTK 내부 로직을 쓰므로, 실제 matcher 를 그대로 통과하는지 우선 확인
    expect(scheduleApiSlice.endpoints.getDaySchedule.matchFulfilled(fulfilledAction as any)).toBe(true);

    const dispatch = runMiddleware(fulfilledAction, '2026-07-13');
    expect(dispatch).toHaveBeenCalledWith(setSavedDateKst('2026-07-14'));
  });

  it('과거 날짜 인자의 getDaySchedule fulfilled(달력 브라우징) → 태그 갱신 안 함', () => {
    (getKstToday as jest.Mock).mockReturnValue('2026-07-14');
    const fulfilledAction = {
      type: 'scheduleApi/executeQuery/fulfilled',
      meta: { arg: { endpointName: 'getDaySchedule', originalArgs: '2026-07-10' } },
    };
    const dispatch = runMiddleware(fulfilledAction, '2026-07-13');
    expect(dispatch).not.toHaveBeenCalled();
  });

  it('getMyGroups fulfilled 만으로는 태그가 전진하지 않는다 (자정 우회 방지)', () => {
    (getKstToday as jest.Mock).mockReturnValue('2026-07-14');
    const fulfilledAction = {
      type: 'caregroupApi/executeQuery/fulfilled',
      meta: { arg: { endpointName: 'getMyGroups', originalArgs: undefined } },
    };
    const dispatch = runMiddleware(fulfilledAction, '2026-07-13');
    expect(dispatch).not.toHaveBeenCalled();
  });
});
