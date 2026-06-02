import { extractRouteFromNotification } from '@/lib/notifications/deepLink';

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
