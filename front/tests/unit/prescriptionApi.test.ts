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
        nameRaw: '아토르바', matchedDrugName: null,
        doseAmount: 10, doseUnit: 'mg', frequency: 1, durationDays: 30, confidence: null,
      }],
    };
    expect(detail.imageUrl).toBeNull();
    expect(detail.drugs[0].matchedDrugName).toBeNull();
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
