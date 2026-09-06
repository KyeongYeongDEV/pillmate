import { configureStore } from '@reduxjs/toolkit';

jest.mock('@/lib/auth/storage', () => ({
  getToken: jest.fn().mockResolvedValue(null),
  getCurrentUserId: jest.fn().mockResolvedValue(1),
  clearAuth: jest.fn().mockResolvedValue(undefined),
}));

jest.mock('expo-router', () => ({ router: { replace: jest.fn() } }));

jest.mock('@reduxjs/toolkit/query/react', () => {
  const actual = jest.requireActual('@reduxjs/toolkit/query/react');
  return { ...actual, fetchBaseQuery: jest.fn() };
});

type ScheduleApiModule = typeof import('@/store/slices/scheduleApi');

describe('scheduleApi — 구성원(patientId) 조회 엔드포인트', () => {
  let scheduleApiSlice: ScheduleApiModule['scheduleApiSlice'];
  let rawBaseQuery: jest.Mock;
  let stores: ReturnType<typeof configureStore>[] = [];

  function makeStore() {
    const store = configureStore({
      reducer: { [scheduleApiSlice.reducerPath]: scheduleApiSlice.reducer },
      middleware: (getDefault) => getDefault().concat(scheduleApiSlice.middleware),
    });
    stores.push(store);
    return store;
  }

  function requestedUrls(): string[] {
    return rawBaseQuery.mock.calls.map(call => call[0] as string);
  }

  beforeEach(() => {
    jest.resetModules();
    jest.useFakeTimers();
    const rtk = jest.requireMock<typeof import('@reduxjs/toolkit/query/react')>(
      '@reduxjs/toolkit/query/react',
    );
    rawBaseQuery = jest.fn().mockResolvedValue({ data: {} });
    (rtk.fetchBaseQuery as jest.Mock).mockReset().mockReturnValue(rawBaseQuery);
    scheduleApiSlice = require('@/store/slices/scheduleApi').scheduleApiSlice;
  });

  // 캐시 유지(keepUnusedDataFor) 타이머가 남으면 Jest 환경 teardown 후 경고가 난다.
  afterEach(() => {
    stores.forEach(store => store.dispatch(scheduleApiSlice.util.resetApiState()));
    stores = [];
    jest.runOnlyPendingTimers();
    jest.useRealTimers();
  });

  it('getMemberDaySchedule / getMemberMonthAdherence 엔드포인트 존재', () => {
    expect(scheduleApiSlice.endpoints).toHaveProperty('getMemberDaySchedule');
    expect(scheduleApiSlice.endpoints).toHaveProperty('getMemberMonthAdherence');
  });

  it('getMemberDaySchedule — URL 에 date + patientId 가 붙는다', async () => {
    const store = makeStore();
    await store.dispatch(
      (scheduleApiSlice.endpoints.getMemberDaySchedule as any).initiate({
        date: '2026-08-20',
        patientId: 7,
      }),
    );
    expect(requestedUrls()).toContain('/schedules/day?date=2026-08-20&patientId=7');
  });

  it('getMemberMonthAdherence — URL 에 month + patientId 가 붙는다', async () => {
    const store = makeStore();
    await store.dispatch(
      (scheduleApiSlice.endpoints.getMemberMonthAdherence as any).initiate({
        month: '2026-08',
        patientId: 7,
      }),
    );
    expect(requestedUrls()).toContain('/schedules/month?month=2026-08&patientId=7');
  });

  it('본인 조회(getDaySchedule) URL 에는 patientId 가 붙지 않는다 — 기존 동작 회귀 방지', async () => {
    const store = makeStore();
    await store.dispatch((scheduleApiSlice.endpoints.getDaySchedule as any).initiate('2026-08-20'));
    expect(requestedUrls()).toContain('/schedules/day?date=2026-08-20');
    expect(requestedUrls().some(url => url.includes('patientId'))).toBe(false);
  });

  it('patientId 별로 캐시 키가 분리된다 — 다른 사람 데이터 혼입 금지', async () => {
    const store = makeStore();
    await Promise.all([
      store.dispatch((scheduleApiSlice.endpoints.getMemberDaySchedule as any).initiate({ date: '2026-08-20', patientId: 7 })),
      store.dispatch((scheduleApiSlice.endpoints.getMemberDaySchedule as any).initiate({ date: '2026-08-20', patientId: 9 })),
    ]);
    const keys = Object.keys((store.getState() as any).scheduleApi.queries);
    expect(keys).toHaveLength(2);
    expect(keys.some(k => k.includes('7'))).toBe(true);
    expect(keys.some(k => k.includes('9'))).toBe(true);
  });

  it('본인 캐시 키와 구성원 캐시 키가 서로 다르다', async () => {
    const store = makeStore();
    await Promise.all([
      store.dispatch((scheduleApiSlice.endpoints.getDaySchedule as any).initiate('2026-08-20')),
      store.dispatch((scheduleApiSlice.endpoints.getMemberDaySchedule as any).initiate({ date: '2026-08-20', patientId: 7 })),
    ]);
    const keys = Object.keys((store.getState() as any).scheduleApi.queries);
    expect(keys).toHaveLength(2);
    expect(keys.filter(k => k.startsWith('getDaySchedule('))).toHaveLength(1);
    expect(keys.filter(k => k.startsWith('getMemberDaySchedule('))).toHaveLength(1);
  });

  it('구성원 응답이 비어도 MOCK_SCHEDULE 로 대체하지 않는다 — 남의 화면에 가짜 복약 표시 금지', async () => {
    const store = makeStore();
    const result: any = await store.dispatch(
      (scheduleApiSlice.endpoints.getMemberDaySchedule as any).initiate({
        date: '2026-08-20',
        patientId: 7,
      }),
    );
    expect(result.data).toEqual({ date: '2026-08-20', totalCount: 0, doneCount: 0, slots: [] });
  });

  it('구성원 조회는 query 전용 — patientId 를 받는 mutation 은 없다', () => {
    const mutationNames = Object.keys(scheduleApiSlice.endpoints).filter(
      name => typeof (scheduleApiSlice.endpoints as any)[name].initiate === 'function'
        && (scheduleApiSlice.endpoints as any)[name].select === undefined,
    );
    expect(mutationNames.some(name => name.toLowerCase().includes('member'))).toBe(false);
  });
});
