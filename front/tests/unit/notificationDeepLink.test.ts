import { extractRouteFromNotification, extractNotificationIdFromNotification } from '@/lib/notifications/deepLink';

describe('extractRouteFromNotification', () => {
  it('content.data.route — 직접 추출', () => {
    const resp = { notification: { request: { content: { data: { route: '/group/4' } } } } };
    expect(extractRouteFromNotification(resp as any)).toBe('/group/4');
  });

  it('data 누락 → null', () => {
    const resp = { notification: { request: { content: {} } } };
    expect(extractRouteFromNotification(resp as any)).toBeNull();
  });

  it('route 누락 → null', () => {
    const resp = { notification: { request: { content: { data: { foo: 'bar' } } } } };
    expect(extractRouteFromNotification(resp as any)).toBeNull();
  });

  it('route 비문자열 → null (안전)', () => {
    const resp = { notification: { request: { content: { data: { route: 42 } } } } };
    expect(extractRouteFromNotification(resp as any)).toBeNull();
  });

  it('빈 응답 → null', () => {
    expect(extractRouteFromNotification(null as any)).toBeNull();
  });

  it('relative route — /prescription/result/7 그대로 반환', () => {
    const resp = { notification: { request: { content: { data: { route: '/prescription/result/7' } } } } };
    expect(extractRouteFromNotification(resp as any)).toBe('/prescription/result/7');
  });
});

// 사용자 요청(2026-09-18) — 푸시 탭으로 앱 진입 시 해당 알림을 읽음 처리해 배지 숫자가 줄어들게 함
describe('extractNotificationIdFromNotification', () => {
  it('content.data.notificationId — 숫자로 파싱해 반환', () => {
    const resp = { notification: { request: { content: { data: { notificationId: '42' } } } } };
    expect(extractNotificationIdFromNotification(resp as any)).toBe(42);
  });

  it('notificationId 누락 → null', () => {
    const resp = { notification: { request: { content: { data: { route: '/home' } } } } };
    expect(extractNotificationIdFromNotification(resp as any)).toBeNull();
  });

  it('notificationId 가 숫자로 파싱 불가("null" 등) → null', () => {
    const resp = { notification: { request: { content: { data: { notificationId: 'null' } } } } };
    expect(extractNotificationIdFromNotification(resp as any)).toBeNull();
  });

  it('빈 응답 → null', () => {
    expect(extractNotificationIdFromNotification(null as any)).toBeNull();
  });
});
