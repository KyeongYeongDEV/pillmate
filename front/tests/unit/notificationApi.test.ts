import { notificationApiSlice, markReadInList } from '@/store/slices/notificationApi';
import { notificationMeta, notificationRoute, unreadCount } from '@/lib/notificationMeta';
import { canNudge, nudgeSuccessMessage, nudgeErrorMessage } from '@/lib/nudge';
import { relativeTime } from '@/utils/relativeTime';
import type { NotificationItem } from '@/types/notification';

const item = (over: Partial<NotificationItem> = {}): NotificationItem => ({
  id: 1, type: 'DOSE_TAKEN', title: '복약 완료', body: '아침약을 드셨어요',
  status: 'SENT', doseLogId: null, actorUserId: null, createdAt: '2026-06-14T08:00:00Z', ...over,
});

describe('notificationApi — 엔드포인트', () => {
  it('getNotifications / markRead / nudgeDose 존재', () => {
    expect(notificationApiSlice.endpoints).toHaveProperty('getNotifications');
    expect(notificationApiSlice.endpoints).toHaveProperty('markRead');
    expect(notificationApiSlice.endpoints).toHaveProperty('nudgeDose');
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

  it('unreadCount — 발송된(SENT) 것만 집계, READ 제외', () => {
    const items = [item({ status: 'SENT' }), item({ id: 2, status: 'READ' }), item({ id: 3, status: 'SENT' })];
    expect(unreadCount(items)).toBe(2);
  });

  // 미발송(PENDING)·실패(FAILED)는 사용자에게 도달하지 않아 목록에서 인지 불가 — 배지에 세면 유령 배지가 된다
  it('unreadCount — PENDING/FAILED 는 제외', () => {
    const items = [item({ status: 'PENDING' }), item({ id: 2, status: 'FAILED' })];
    expect(unreadCount(items)).toBe(0);
  });
});

describe('canNudge — 넛지 버튼 표시 조건', () => {
  const me = 7;
  const overdueByOther = () => item({ type: 'DOSE_OVERDUE', doseLogId: 42, actorUserId: 9 });

  it('다른 그룹원의 DOSE_OVERDUE → 표시', () => {
    expect(canNudge(overdueByOther(), me)).toBe(true);
  });

  it('내 자신의 미복용(actorUserId === 나)은 넛지 불가', () => {
    expect(canNudge(item({ type: 'DOSE_OVERDUE', doseLogId: 42, actorUserId: me }), me)).toBe(false);
  });

  it('DOSE_OVERDUE 아닌 타입은 불가', () => {
    expect(canNudge(item({ type: 'DOSE_NUDGE', doseLogId: 42, actorUserId: 9 }), me)).toBe(false);
    expect(canNudge(item({ type: 'DOSE_MISSED', doseLogId: 42, actorUserId: 9 }), me)).toBe(false);
  });

  it('doseLogId 없으면 불가', () => {
    expect(canNudge(item({ type: 'DOSE_OVERDUE', doseLogId: null, actorUserId: 9 }), me)).toBe(false);
  });

  it('actorUserId 없거나 현재 userId 없으면 불가', () => {
    expect(canNudge(item({ type: 'DOSE_OVERDUE', doseLogId: 42, actorUserId: null }), me)).toBe(false);
    expect(canNudge(overdueByOther(), null)).toBe(false);
  });
});

describe('nudge 응답/에러 → 토스트 메시지 매핑', () => {
  it('200 alreadyNotified=false → 전송 성공 문구', () => {
    expect(nudgeSuccessMessage({ alreadyNotified: false })).toBe('약 드시라고 알림을 보냈어요');
  });
  it('200 alreadyNotified=true → 이미 전달 문구', () => {
    expect(nudgeSuccessMessage({ alreadyNotified: true })).toBe('이미 복약 알림이 전달됐어요');
  });
  it('429/409/403 → 상태별 문구', () => {
    expect(nudgeErrorMessage(429)).toBe('방금 알림을 보냈어요. 잠시 후 다시 시도해 주세요.');
    expect(nudgeErrorMessage(409)).toBe('이미 복용한 약이에요.');
    expect(nudgeErrorMessage(403)).toBe('알림을 보낼 권한이 없어요.');
  });
  it('알 수 없는 상태 → 폴백 문구', () => {
    expect(nudgeErrorMessage(500)).toBe('알림을 보내지 못했어요. 잠시 후 다시 시도해 주세요.');
    expect(nudgeErrorMessage(undefined)).toBe('알림을 보내지 못했어요. 잠시 후 다시 시도해 주세요.');
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
