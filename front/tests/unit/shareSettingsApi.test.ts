import {
  caregroupApiSlice,
  shareSettingsUrl,
  updatePrescriptionShareRequest,
  applyPrescriptionShareToggle,
} from '@/store/slices/caregroupApi';
import type { ShareablePrescriptionView } from '@/store/slices/caregroupApi';

const prescription = (over: Partial<ShareablePrescriptionView> = {}): ShareablePrescriptionView => ({
  prescriptionId: 2, label: '감기약', prescribedAt: '2026-09-01', shared: false, status: 'ONGOING', ...over,
});

describe('shareSettings — 엔드포인트 존재', () => {
  it('getShareablePrescriptions / updatePrescriptionShare 등록', () => {
    expect(caregroupApiSlice.endpoints).toHaveProperty('getShareablePrescriptions');
    expect(caregroupApiSlice.endpoints).toHaveProperty('updatePrescriptionShare');
  });

  it('initiate 가 thunk 를 반환', () => {
    const q = (caregroupApiSlice.endpoints.getShareablePrescriptions as any).initiate(5);
    const m = (caregroupApiSlice.endpoints.updatePrescriptionShare as any).initiate({
      groupId: 5, prescriptionId: 2, enabled: true,
    });
    expect(typeof q).toBe('function');
    expect(typeof m).toBe('function');
  });
});

describe('shareSettings — 계약 (URL·바디)', () => {
  it('GET /groups/{id}/share-settings', () => {
    expect(shareSettingsUrl(5)).toBe('/groups/5/share-settings');
  });

  it('PUT /groups/{id}/share-settings/{prescriptionId} body {enabled}', () => {
    const req = updatePrescriptionShareRequest({ groupId: 5, prescriptionId: 2, enabled: true });
    expect(req.url).toBe('/groups/5/share-settings/2');
    expect(req.method).toBe('PUT');
    expect(req.body).toEqual({ enabled: true });
  });

  it('끄기 — enabled:false 전달', () => {
    const req = updatePrescriptionShareRequest({ groupId: 9, prescriptionId: 7, enabled: false });
    expect(req.url).toBe('/groups/9/share-settings/7');
    expect(req.body).toEqual({ enabled: false });
  });
});

describe('applyPrescriptionShareToggle — 낙관적 토글 매핑', () => {
  it('해당 prescriptionId 만 shared 변경 (켜기)', () => {
    const list = [prescription({ prescriptionId: 2, shared: false }), prescription({ prescriptionId: 3, shared: false })];
    applyPrescriptionShareToggle(list, 2, true);
    expect(list.find(p => p.prescriptionId === 2)?.shared).toBe(true);
    expect(list.find(p => p.prescriptionId === 3)?.shared).toBe(false);
  });

  it('끄기 반영', () => {
    const list = [prescription({ prescriptionId: 2, shared: true })];
    applyPrescriptionShareToggle(list, 2, false);
    expect(list[0].shared).toBe(false);
  });

  it('없는 prescriptionId 는 변화 없음 (롤백 안전)', () => {
    const list = [prescription({ prescriptionId: 2, shared: false })];
    applyPrescriptionShareToggle(list, 99, true);
    expect(list[0].shared).toBe(false);
  });

  it('롤백 시나리오 — 낙관적 켜기 후 원상복구', () => {
    const list = [prescription({ prescriptionId: 2, shared: false })];
    applyPrescriptionShareToggle(list, 2, true);   // 낙관적 반영
    expect(list[0].shared).toBe(true);
    applyPrescriptionShareToggle(list, 2, false);  // 실패 → undo 로 되돌린 결과와 동치
    expect(list[0].shared).toBe(false);
  });
});
