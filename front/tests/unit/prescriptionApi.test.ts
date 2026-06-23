import { configureStore } from '@reduxjs/toolkit';
import { prescriptionApiSlice } from '@/store/slices/prescriptionApi';
import type { PrescriptionSummary, PrescriptionDetailView } from '@/types/prescription';
import type { AliasLog } from '@/hooks/usePrescriptionReview';

describe('prescriptionApi — 목록·상세 엔드포인트', () => {
  it('getPrescriptions / getPrescriptionDetail 엔드포인트 존재', () => {
    const endpoints = prescriptionApiSlice.endpoints;
    expect(endpoints).toHaveProperty('getPrescriptions');
    expect(endpoints).toHaveProperty('getPrescriptionDetail');
  });

  it('getPrescriptions — initiate() 호출 가능 + thunk 반환', () => {
    const action = (prescriptionApiSlice.endpoints.getPrescriptions as any).initiate();
    expect(typeof action).toBe('function');
  });

  it('getPrescriptionDetail — initiate(id) 호출 가능 + thunk 반환', () => {
    const action = (prescriptionApiSlice.endpoints.getPrescriptionDetail as any).initiate(3);
    expect(typeof action).toBe('function');
  });

  it('PrescriptionSummary — 필드 구조 검증', () => {
    const summary: PrescriptionSummary = {
      id: 1, prescribedAt: '2026-06-14', ocrStatus: 'DONE',
      drugCount: 2, drugNames: '타이레놀 외 1건', createdAt: '2026-06-14T08:00:00Z',
    };
    expect(summary.drugCount).toBe(2);
    expect(summary.drugNames).toContain('타이레놀');
  });

  it('PrescriptionDetailView — matchedDrugName / imageUrl null 허용', () => {
    const detail: PrescriptionDetailView = {
      id: 1, prescribedAt: '2026-06-14', ocrStatus: 'MANUAL', imageUrl: null,
      drugs: [{
        nameRaw: '아토르바', matchedDrugName: null, matchedKdCode: null,
        doseAmount: 10, doseUnit: 'mg', frequency: 1, durationDays: 30, confidence: null,
      }],
    };
    expect(detail.imageUrl).toBeNull();
    expect(detail.drugs[0].matchedDrugName).toBeNull();
  });
});

describe('prescriptionApi — registerPrescription transformResponse (실제 endpoint 직접 실행)', () => {
  // RTK Query 내부 구독 setTimeout 이 테스트 환경 종료 후 발화하지 않도록 fake timers 사용
  beforeEach(() => { jest.useFakeTimers(); });
  afterEach(() => {
    jest.runAllTimers();
    jest.useRealTimers();
    jest.unmock('@/lib/api/baseQuery');
  });

  it('registerPrescription 엔드포인트 존재', () => {
    expect(prescriptionApiSlice.endpoints).toHaveProperty('registerPrescription');
  });

  it('실제 transformResponse — raw ApiEnvelope.data.warnings 가 그대로 보존됨', async () => {
    const WARNING = {
      drugCodeA: 'KD001', drugCodeB: 'KD002', nameA: '와파린', nameB: '아스피린',
      severity: 'CRITICAL', description: '출혈 위험', source: '식약처',
    };
    const rawEnvelope = { data: { prescriptionId: 42, ocrStatus: 'DONE', items: [], warnings: [WARNING] } };

    // baseQuery 를 mock 한 신선한 슬라이스 생성 → 실제 transformResponse 실행 경로 사용
    let testSlice: any;
    jest.isolateModules(() => {
      jest.doMock('@/lib/api/baseQuery', () => ({
        createPillmateBaseQuery: () => async () => ({ data: rawEnvelope }),
      }));
      testSlice = require('@/store/slices/prescriptionApi').prescriptionApiSlice;
    });

    const store = configureStore({
      reducer: { [testSlice.reducerPath]: testSlice.reducer },
      middleware: (gDM: any) => gDM().concat(testSlice.middleware),
    });

    const result: any = await store.dispatch(
      testSlice.endpoints.registerPrescription.initiate({} as any),
    );

    expect(result.data?.prescriptionId).toBe(42);
    expect(result.data?.warnings).toHaveLength(1);
    expect(result.data?.warnings[0].severity).toBe('CRITICAL');
  });

  it('실제 transformResponse — data null 시 warnings [] fallback', async () => {
    let testSlice: any;
    jest.isolateModules(() => {
      jest.doMock('@/lib/api/baseQuery', () => ({
        createPillmateBaseQuery: () => async () => ({ data: null }),
      }));
      testSlice = require('@/store/slices/prescriptionApi').prescriptionApiSlice;
    });

    const store = configureStore({
      reducer: { [testSlice.reducerPath]: testSlice.reducer },
      middleware: (gDM: any) => gDM().concat(testSlice.middleware),
    });

    const result: any = await store.dispatch(
      testSlice.endpoints.registerPrescription.initiate({} as any),
    );

    expect(result.data?.warnings).toEqual([]);
    expect(result.data?.prescriptionId).toBe(0);
  });
});

describe('prescriptionApi — 별칭 학습 로그 (#164)', () => {
  it('logAlias 엔드포인트 존재 + initiate 호출 가능', () => {
    expect(prescriptionApiSlice.endpoints).toHaveProperty('logAlias');
    const log: AliasLog = { nameRaw: '아토르바', itemSeq: '202103611' };
    const action = (prescriptionApiSlice.endpoints.logAlias as any).initiate(log);
    expect(typeof action).toBe('function');
  });

  it('AliasLog — BE RegisterAliasRequest 와 정합 (nameRaw + itemSeq)', () => {
    const log: AliasLog = { nameRaw: '테스트', itemSeq: '202103611' };
    expect(Object.keys(log).sort()).toEqual(['itemSeq', 'nameRaw']);
  });
});
