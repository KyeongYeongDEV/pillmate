import {
  caregroupApiSlice,
  shareSettingsUrl,
  updateShareSettingRequest,
  applyShareToggle,
} from '@/store/slices/caregroupApi';
import type { ShareSettingView } from '@/types/caregroup';

const member = (over: Partial<ShareSettingView> = {}): ShareSettingView => ({
  userId: 2, name: '박순자', role: 'PATIENT', shared: false, ...over,
});

describe('shareSettings — 엔드포인트 존재', () => {
  it('getShareSettings / updateShareSetting 등록', () => {
    expect(caregroupApiSlice.endpoints).toHaveProperty('getShareSettings');
    expect(caregroupApiSlice.endpoints).toHaveProperty('updateShareSetting');
  });

  it('initiate 가 thunk 를 반환', () => {
    const q = (caregroupApiSlice.endpoints.getShareSettings as any).initiate(5);
    const m = (caregroupApiSlice.endpoints.updateShareSetting as any).initiate({
      groupId: 5, viewerUserId: 2, enabled: true,
    });
    expect(typeof q).toBe('function');
    expect(typeof m).toBe('function');
  });
});

describe('shareSettings — 계약 (URL·바디)', () => {
  it('GET /groups/{id}/share-settings', () => {
    expect(shareSettingsUrl(5)).toBe('/groups/5/share-settings');
  });

  it('PUT /groups/{id}/share-settings/{viewerUserId} body {enabled}', () => {
    const req = updateShareSettingRequest({ groupId: 5, viewerUserId: 2, enabled: true });
    expect(req.url).toBe('/groups/5/share-settings/2');
    expect(req.method).toBe('PUT');
    expect(req.body).toEqual({ enabled: true });
  });

  it('끄기 — enabled:false 전달', () => {
    const req = updateShareSettingRequest({ groupId: 9, viewerUserId: 7, enabled: false });
    expect(req.url).toBe('/groups/9/share-settings/7');
    expect(req.body).toEqual({ enabled: false });
  });
});

describe('applyShareToggle — 낙관적 토글 매핑', () => {
  it('해당 viewerUserId 만 shared 변경 (켜기)', () => {
    const list = [member({ userId: 2, shared: false }), member({ userId: 3, shared: false })];
    applyShareToggle(list, 2, true);
    expect(list.find(s => s.userId === 2)?.shared).toBe(true);
    expect(list.find(s => s.userId === 3)?.shared).toBe(false);
  });

  it('끄기 반영', () => {
    const list = [member({ userId: 2, shared: true })];
    applyShareToggle(list, 2, false);
    expect(list[0].shared).toBe(false);
  });

  it('없는 userId 는 변화 없음 (롤백 안전)', () => {
    const list = [member({ userId: 2, shared: false })];
    applyShareToggle(list, 99, true);
    expect(list[0].shared).toBe(false);
  });

  it('롤백 시나리오 — 낙관적 켜기 후 원상복구', () => {
    const list = [member({ userId: 2, shared: false })];
    applyShareToggle(list, 2, true);   // 낙관적 반영
    expect(list[0].shared).toBe(true);
    applyShareToggle(list, 2, false);  // 실패 → undo 로 되돌린 결과와 동치
    expect(list[0].shared).toBe(false);
  });
});
