import type { MyGroupSummary } from '@/types/caregroup';
import { applyGroupFilter } from '@/lib/groupFilter';

const G_PATIENT: MyGroupSummary = {
  groupId: 1, name: '아빠 그룹', role: 'PATIENT',
  memberCount: 3, membersPreview: ['아', '엄', '나'],
  lastActivity: null, unreadCount: 0, pinned: false,
};

const G_GUARDIAN: MyGroupSummary = {
  groupId: 2, name: '할머니 댁', role: 'GUARDIAN',
  memberCount: 3, membersPreview: ['할', '엄', '나'],
  lastActivity: null, unreadCount: 0, pinned: true,
};

const G_ADMIN: MyGroupSummary = {
  groupId: 3, name: '우리 가족', role: 'ADMIN',
  memberCount: 2, membersPreview: ['나', '동'],
  lastActivity: null, unreadCount: 0, pinned: false,
};

const G_PRIVATE: MyGroupSummary = {
  groupId: 4, name: '내 복약', role: 'ADMIN',
  memberCount: 1, membersPreview: ['나'],
  lastActivity: null, unreadCount: 0, pinned: false,
};

const GROUPS = [G_PATIENT, G_GUARDIAN, G_ADMIN, G_PRIVATE];

describe('applyGroupFilter', () => {
  it('전체 — 모든 그룹 반환', () => {
    const result = applyGroupFilter(GROUPS, '전체');
    expect(result).toHaveLength(4);
  });

  it('내가 환자 — role=PATIENT 만', () => {
    const result = applyGroupFilter(GROUPS, '내가 환자');
    expect(result).toEqual([G_PATIENT]);
  });

  it('내가 보호자 — role IN (ADMIN, GUARDIAN)', () => {
    const result = applyGroupFilter(GROUPS, '내가 보호자');
    expect(result).toEqual([G_GUARDIAN, G_ADMIN, G_PRIVATE]);
  });

  it('비공개 — memberCount===1 만', () => {
    const result = applyGroupFilter(GROUPS, '비공개');
    expect(result).toEqual([G_PRIVATE]);
  });

  it('비공개 — memberCount>1 그룹 제외', () => {
    const result = applyGroupFilter([G_GUARDIAN, G_ADMIN], '비공개');
    expect(result).toEqual([]);
  });

  it('내가 환자 — GUARDIAN 그룹 제외', () => {
    const result = applyGroupFilter([G_GUARDIAN, G_ADMIN], '내가 환자');
    expect(result).toEqual([]);
  });
});
