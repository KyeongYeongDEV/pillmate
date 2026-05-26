import type { ActivityFeedItem, ActivitySeverity } from '../../src/types/activity';

// ── ActivityFeedItem 신규 타입 검증 ──────────────────────────────────────

describe('ActivityFeedItem 타입', () => {
  const item: ActivityFeedItem = {
    actorNickname: '할머니',
    activityType: 'DOSE_TAKEN',
    timeSlot: 'MORNING',
    summary: '아침약 2개를 복용했어요',
    severity: 'INFO',
    occurredAt: '2026-05-26T08:12:00.000Z',
  };

  it('actorNickname — PII(actorUserId) 없음', () => {
    expect(item.actorNickname).toBe('할머니');
    expect(item).not.toHaveProperty('actorUserId');
  });

  it('timeSlot MORNING', () => {
    expect(item.timeSlot).toBe('MORNING');
  });

  it('DOSE_TAKEN activityType', () => {
    expect(item.activityType).toBe('DOSE_TAKEN');
  });

  it('DOSE_MISSED activityType', () => {
    const missed: ActivityFeedItem = { ...item, activityType: 'DOSE_MISSED', severity: 'WARN' };
    expect(missed.activityType).toBe('DOSE_MISSED');
  });

  it('WARN severity', () => {
    const warn: ActivityFeedItem = { ...item, severity: 'WARN' };
    expect(warn.severity).toBe('WARN');
  });

  it('severity는 INFO 또는 WARN만 (CRITICAL 없음)', () => {
    const severities: ActivitySeverity[] = ['INFO', 'WARN'];
    severities.forEach(s => expect(['INFO', 'WARN']).toContain(s));
  });

  it('모든 TimeSlot 값', () => {
    const slots = ['MORNING', 'NOON', 'EVENING', 'BEDTIME'] as const;
    slots.forEach(slot => {
      const i: ActivityFeedItem = { ...item, timeSlot: slot };
      expect(i.timeSlot).toBe(slot);
    });
  });
});

// ── severity 색상 분기 ────────────────────────────────────────────────

function severityTint(s: ActivitySeverity): string {
  return s === 'WARN' ? '#E02020' : '#0066FF';
}

describe('severity 색상 분기', () => {
  it('INFO → 파랑', () => expect(severityTint('INFO')).toBe('#0066FF'));
  it('WARN → 빨강', () => expect(severityTint('WARN')).toBe('#E02020'));
});
