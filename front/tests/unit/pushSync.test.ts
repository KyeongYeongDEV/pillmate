import { handlePushReceived } from '@/lib/notifications/pushSync';

function notif(type?: string) {
  return { request: { content: { data: type ? { type } : {} } } };
}

describe('handlePushReceived — 포그라운드 푸시 cache invalidate', () => {
  it('DOSE_* → activity + caregroup + notification 무효화', () => {
    const dispatch = jest.fn();
    handlePushReceived(notif('DOSE_TAKEN'), dispatch);
    const types = dispatch.mock.calls.map(c => c[0]?.type);
    expect(types).toContain('activityApi/invalidateTags');
    expect(types).toContain('caregroupApi/invalidateTags');
    expect(types).toContain('notificationApi/invalidateTags');
  });

  it('DOSE_CANCELED 도 DOSE_* 분기', () => {
    const dispatch = jest.fn();
    handlePushReceived(notif('DOSE_CANCELED'), dispatch);
    const types = dispatch.mock.calls.map(c => c[0]?.type);
    expect(types).toContain('activityApi/invalidateTags');
    expect(types).toContain('caregroupApi/invalidateTags');
  });

  it('비-DOSE 타입 → notification 만 무효화', () => {
    const dispatch = jest.fn();
    handlePushReceived(notif('GROUP_INVITE'), dispatch);
    const types = dispatch.mock.calls.map(c => c[0]?.type);
    expect(types).toEqual(['notificationApi/invalidateTags']);
  });

  it('type 없음 → notification 만', () => {
    const dispatch = jest.fn();
    handlePushReceived(notif(), dispatch);
    const types = dispatch.mock.calls.map(c => c[0]?.type);
    expect(types).toEqual(['notificationApi/invalidateTags']);
  });
});
