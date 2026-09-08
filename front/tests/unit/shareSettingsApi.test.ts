import {
  caregroupApiSlice,
  shareSettingsUrl,
  updateMemberShareRequest,
  updatePrescriptionShareRequest,
  applyMemberShareToggle,
  applyPrescriptionShareToggle,
} from '@/store/slices/caregroupApi';
import type { ShareSettingsView } from '@/store/slices/caregroupApi';

const view = (over: Partial<ShareSettingsView> = {}): ShareSettingsView => ({
  members: [
    { userId: 7, name: '박순자', role: 'PATIENT', shared: false },
    { userId: 2, name: '김철수', role: 'GUARDIAN', shared: true },
  ],
  prescriptions: [
    { prescriptionId: 2, label: '감기약', prescribedAt: '2026-09-01', shared: false, status: 'ONGOING' },
    { prescriptionId: 3, label: '당뇨약', prescribedAt: '2026-09-03', shared: false, status: 'ONGOING' },
  ],
  ...over,
});

describe('shareSettings — 엔드포인트 존재', () => {
  it('getShareSettings / updateMemberShare / updatePrescriptionShare 등록', () => {
    expect(caregroupApiSlice.endpoints).toHaveProperty('getShareSettings');
    expect(caregroupApiSlice.endpoints).toHaveProperty('updateMemberShare');
    expect(caregroupApiSlice.endpoints).toHaveProperty('updatePrescriptionShare');
  });

  it('initiate 가 thunk 를 반환', () => {
    const q = (caregroupApiSlice.endpoints.getShareSettings as any).initiate(5);
    const mm = (caregroupApiSlice.endpoints.updateMemberShare as any).initiate({
      groupId: 5, viewerUserId: 7, enabled: true,
    });
    const mp = (caregroupApiSlice.endpoints.updatePrescriptionShare as any).initiate({
      groupId: 5, prescriptionId: 2, enabled: true,
    });
    expect(typeof q).toBe('function');
    expect(typeof mm).toBe('function');
    expect(typeof mp).toBe('function');
  });
});

describe('shareSettings — 계약 (URL·바디)', () => {
  it('GET /groups/{id}/share-settings', () => {
    expect(shareSettingsUrl(5)).toBe('/groups/5/share-settings');
  });

  it('PUT /groups/{id}/share-settings/members/{viewerUserId} body {enabled}', () => {
    const req = updateMemberShareRequest({ groupId: 5, viewerUserId: 7, enabled: true });
    expect(req.url).toBe('/groups/5/share-settings/members/7');
    expect(req.method).toBe('PUT');
    expect(req.body).toEqual({ enabled: true });
  });

  it('PUT /groups/{id}/share-settings/prescriptions/{prescriptionId} body {enabled}', () => {
    const req = updatePrescriptionShareRequest({ groupId: 5, prescriptionId: 2, enabled: true });
    expect(req.url).toBe('/groups/5/share-settings/prescriptions/2');
    expect(req.method).toBe('PUT');
    expect(req.body).toEqual({ enabled: true });
  });

  it('끄기 — enabled:false 전달', () => {
    const rm = updateMemberShareRequest({ groupId: 9, viewerUserId: 3, enabled: false });
    const rp = updatePrescriptionShareRequest({ groupId: 9, prescriptionId: 7, enabled: false });
    expect(rm.url).toBe('/groups/9/share-settings/members/3');
    expect(rm.body).toEqual({ enabled: false });
    expect(rp.url).toBe('/groups/9/share-settings/prescriptions/7');
    expect(rp.body).toEqual({ enabled: false });
  });
});

describe('applyMemberShareToggle — 낙관적 토글 매핑', () => {
  it('해당 userId 만 shared 변경 (켜기)', () => {
    const v = view();
    applyMemberShareToggle(v, 7, true);
    expect(v.members.find(m => m.userId === 7)?.shared).toBe(true);
    expect(v.members.find(m => m.userId === 2)?.shared).toBe(true);
  });

  it('없는 userId 는 변화 없음 (롤백 안전)', () => {
    const v = view();
    applyMemberShareToggle(v, 999, true);
    expect(v.members.find(m => m.userId === 7)?.shared).toBe(false);
  });

  it('롤백 시나리오 — 낙관적 켜기 후 원상복구', () => {
    const v = view();
    applyMemberShareToggle(v, 7, true);
    expect(v.members.find(m => m.userId === 7)?.shared).toBe(true);
    applyMemberShareToggle(v, 7, false);
    expect(v.members.find(m => m.userId === 7)?.shared).toBe(false);
  });
});

describe('applyPrescriptionShareToggle — 낙관적 토글 매핑', () => {
  it('해당 prescriptionId 만 shared 변경 (켜기)', () => {
    const v = view();
    applyPrescriptionShareToggle(v, 2, true);
    expect(v.prescriptions.find(p => p.prescriptionId === 2)?.shared).toBe(true);
    expect(v.prescriptions.find(p => p.prescriptionId === 3)?.shared).toBe(false);
  });

  it('없는 prescriptionId 는 변화 없음 (롤백 안전)', () => {
    const v = view();
    applyPrescriptionShareToggle(v, 99, true);
    expect(v.prescriptions.find(p => p.prescriptionId === 2)?.shared).toBe(false);
  });

  it('롤백 시나리오 — 낙관적 켜기 후 원상복구', () => {
    const v = view();
    applyPrescriptionShareToggle(v, 2, true);
    expect(v.prescriptions.find(p => p.prescriptionId === 2)?.shared).toBe(true);
    applyPrescriptionShareToggle(v, 2, false);
    expect(v.prescriptions.find(p => p.prescriptionId === 2)?.shared).toBe(false);
  });
});
