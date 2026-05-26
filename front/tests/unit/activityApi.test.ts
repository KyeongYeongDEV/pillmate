import type { ActivityFeedItem, ActivitySeverity } from '../../src/types/activity';

// ── ActivityFeedItem 타입 검증 ──────────────────────────────────────────

describe('ActivityFeedItem type', () => {
  const item: ActivityFeedItem = {
    id: 1,
    actorUserId: 2,
    actorName: '박순자',
    activityType: 'DOSE_TAKEN',
    summary: '아침약 2개를 복용했어요',
    severity: 'INFO',
    occurredAt: '2026-05-26T08:12:00.000Z',
  };

  it('INFO severity item 구조 OK', () => {
    expect(item.severity).toBe('INFO');
    expect(item.actorUserId).toBe(2);
    expect(item.activityType).toBe('DOSE_TAKEN');
  });

  it('WARN severity item', () => {
    const warn: ActivityFeedItem = { ...item, id: 2, severity: 'WARN', activityType: 'DOSE_MISSED', summary: '취침 전 약을 놓치셨어요' };
    expect(warn.severity).toBe('WARN');
  });

  it('CRITICAL severity item', () => {
    const critical: ActivityFeedItem = { ...item, id: 3, severity: 'CRITICAL', summary: '혈압약 3일 연속 미복용' };
    expect(critical.severity).toBe('CRITICAL');
  });

  it('PRESCRIPTION_REGISTERED activityType', () => {
    const rx: ActivityFeedItem = { ...item, id: 4, activityType: 'PRESCRIPTION_REGISTERED' };
    expect(rx.activityType).toBe('PRESCRIPTION_REGISTERED');
  });
});

// ── severity 색상 로직 (컴포넌트에서 사용하는 helper 인라인 검증) ───────

function severityTint(s: ActivitySeverity): string {
  if (s === 'CRITICAL') return '#E02020';
  if (s === 'WARN') return '#F5A623';
  return '#0066FF';
}

describe('severity 색상 분기', () => {
  it('INFO → 파랑', () => expect(severityTint('INFO')).toBe('#0066FF'));
  it('WARN → 노랑', () => expect(severityTint('WARN')).toBe('#F5A623'));
  it('CRITICAL → 빨강', () => expect(severityTint('CRITICAL')).toBe('#E02020'));
});
