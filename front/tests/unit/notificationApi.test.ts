import { notificationApiSlice, markReadInList } from '@/store/slices/notificationApi';
import { notificationMeta, notificationRoute, unreadCount } from '@/lib/notificationMeta';
import { relativeTime } from '@/utils/relativeTime';
import type { NotificationItem } from '@/types/notification';

const item = (over: Partial<NotificationItem> = {}): NotificationItem => ({
  id: 1, type: 'DOSE_TAKEN', title: '복약 완료', body: '아침약을 드셨어요',
  status: 'SENT', doseLogId: null, createdAt: '2026-06-14T08:00:00Z', ...over,
});

describe('notificationApi — 엔드포인트', () => {
  it('getNotifications / markRead 존재', () => {
    expect(notificationApiSlice.endpoints).toHaveProperty('getNotifications');
    expect(notificationApiSlice.endpoints).toHaveProperty('markRead');
  });

  it('markRead — initiate(id) thunk 반환', () => {
    const action = (notificationApiSlice.endpoints.markRead as any).initiate(5);
    expect(typeof action).toBe('function');
  });

  it('reducerPath 등록', () => {
    expect(notificationApiSlice.reducerPath).toBe('notificationApi');
  });
});

describe('markReadInList — 낙관적 읽음 처리', () => {
  it('해당 id 알림만 READ로 변경', () => {
    const items = [item({ id: 1, status: 'SENT' }), item({ id: 2, status: 'SENT' })];
    markReadInList(items, 1);
    expect(items.find(n => n.id === 1)?.status).toBe('READ');
    expect(items.find(n => n.id === 2)?.status).toBe('SENT');
  });

  it('없는 id는 변화 없음', () => {
    const items = [item({ id: 1, status: 'SENT' })];
    markReadInList(items, 99);
    expect(items[0].status).toBe('SENT');
  });
});

describe('notificationMeta / route / unreadCount', () => {
  it('type별 아이콘·색 매핑', () => {
    expect(notificationMeta('DDI_CRITICAL').icon).toBe('alert-triangle');
    expect(notificationMeta('PRESCRIPTION_NEW').color).toBeTruthy();
  });

  it('doseLogId 있으면 복약 화면 라우트', () => {
    expect(notificationRoute(item({ doseLogId: 9 }))).toBe('/(tabs)/schedule');
  });

  it('PRESCRIPTION_NEW → 처방전 탭', () => {
    expect(notificationRoute(item({ type: 'PRESCRIPTION_NEW', doseLogId: null }))).toBe('/(tabs)/prescriptions');
  });

  it('매핑 없으면 null (읽음만)', () => {
    expect(notificationRoute(item({ type: 'WEEKLY_REPORT', doseLogId: null }))).toBeNull();
  });

  it('unreadCount — READ 제외', () => {
    const items = [item({ status: 'SENT' }), item({ id: 2, status: 'READ' }), item({ id: 3, status: 'PENDING' })];
    expect(unreadCount(items)).toBe(2);
  });
});

describe('relativeTime', () => {
  const now = new Date('2026-06-14T10:00:00Z').getTime();
  it('1분 미만 → 방금', () => {
    expect(relativeTime('2026-06-14T09:59:30Z', now)).toBe('방금');
  });
  it('분/시간/일 경계', () => {
    expect(relativeTime('2026-06-14T09:30:00Z', now)).toBe('30분 전');
    expect(relativeTime('2026-06-14T07:00:00Z', now)).toBe('3시간 전');
    expect(relativeTime('2026-06-12T10:00:00Z', now)).toBe('2일 전');
  });
});
