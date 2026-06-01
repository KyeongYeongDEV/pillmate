import { resolveEventStyle, isPersonalGroup, composeGroupDesc } from '@/lib/groupCardHelpers';
import { colors } from '@/styles/tokens';
import type { MyGroupSummary } from '@/types/caregroup';

const baseGroup: MyGroupSummary = {
  groupId: 1, name: '할머니 댁', role: 'GUARDIAN',
  memberCount: 3, membersPreview: ['엄', '딸', '아들'],
  lastActivity: null, unreadCount: 0, pinned: false,
};

describe('resolveEventStyle', () => {
  it('DOSE_TAKEN → done 색상 (green-95/green-40/positive)', () => {
    const style = resolveEventStyle('DOSE_TAKEN');
    expect(style.bg).toBe(colors.green95);
    expect(style.fg).toBe(colors.green40);
    expect(style.dot).toBe(colors.statusPositive);
  });

  it('DOSE_MISSED → miss 색상 (red-95/red-40/negative)', () => {
    const style = resolveEventStyle('DOSE_MISSED');
    expect(style.bg).toBe(colors.red95);
    expect(style.fg).toBe(colors.red40);
    expect(style.dot).toBe(colors.statusNegative);
  });

  it('AI_INSIGHT → ai 색상 (violet)', () => {
    const style = resolveEventStyle('AI_INSIGHT');
    expect(style.bg).toBe(colors.violet95);
    expect(style.fg).toBe(colors.violet45);
  });

  it('unknown type → default (note 스타일 fallback)', () => {
    const style = resolveEventStyle('UNKNOWN_TYPE_XYZ');
    expect(style.bg).toBe(colors.yellow95);
  });

  it('undefined → default fallback', () => {
    const style = resolveEventStyle(undefined);
    expect(style.bg).toBe(colors.yellow95);
  });
});

describe('isPersonalGroup', () => {
  it('memberCount=1 → personal', () => {
    expect(isPersonalGroup({ ...baseGroup, memberCount: 1 })).toBe(true);
  });

  it('memberCount=2 → not personal', () => {
    expect(isPersonalGroup({ ...baseGroup, memberCount: 2 })).toBe(false);
  });
});

describe('composeGroupDesc', () => {
  it('memberCount=1 → "본인만 · 비공개"', () => {
    expect(composeGroupDesc({ ...baseGroup, memberCount: 1 })).toBe('본인만 · 비공개');
  });

  it('membersPreview 3개 → "N명 · 엄·딸·아들"', () => {
    expect(composeGroupDesc(baseGroup)).toBe('3명 · 엄·딸·아들');
  });

  it('membersPreview 빈 배열 → "N명"', () => {
    expect(composeGroupDesc({ ...baseGroup, membersPreview: [] })).toBe('3명');
  });
});
