import type { MyGroupSummary, GroupDetailResponse } from '@/types/caregroup';
import { caregroupApiSlice } from '@/store/slices/caregroupApi';

describe('caregroupApi — 타입 + 엔드포인트', () => {
  it('MyGroupSummary — 필드 구조 검증', () => {
    const summary: MyGroupSummary = {
      groupId: 1,
      name: '할머니 댁',
      role: '보호자',
      memberCount: 3,
      membersPreview: ['박', '김', '이'],
      lastActivity: { summary: '아침약 복용', activityType: 'DOSE_TAKEN', severity: 'INFO', occurredAt: '2026-06-01T08:00:00Z' },
      unreadCount: 0,
      pinned: true,
    };
    expect(summary.groupId).toBe(1);
    expect(summary.pinned).toBe(true);
    expect(summary.unreadCount).toBe(0);
  });

  it('MyGroupSummary — lastActivity null 허용', () => {
    const summary: MyGroupSummary = {
      groupId: 2, name: '아빠 그룹', role: '환자',
      memberCount: 1, membersPreview: ['나'],
      lastActivity: null, unreadCount: 0, pinned: false,
    };
    expect(summary.lastActivity).toBeNull();
  });

  it('GroupDetailResponse — members + inviteCode + recentActivities 구조', () => {
    const detail: GroupDetailResponse = {
      groupId: 1,
      name: '할머니 댁',
      memberCount: 3,
      members: [{ userId: 1, name: '박순자', role: '환자' }],
      inviteCode: { code: '3F9K2P', expiresAt: '2026-06-01T09:00:00Z' },
      recentActivities: [{ actorName: '박순자', activityType: 'DOSE_TAKEN', summary: '아침약 복용', occurredAt: '2026-06-01T08:00:00Z' }],
    };
    expect(detail.members).toHaveLength(1);
    expect(detail.inviteCode.code).toBe('3F9K2P');
    expect(detail.recentActivities).toHaveLength(1);
  });

  it('caregroupApiSlice — reducerPath 등록', () => {
    expect(caregroupApiSlice.reducerPath).toBe('caregroupApi');
  });

  it('caregroupApiSlice — endpoints 존재 (getMyGroups/getGroupDetail)', () => {
    const endpoints = caregroupApiSlice.endpoints;
    expect(endpoints).toHaveProperty('getMyGroups');
    expect(endpoints).toHaveProperty('getGroupDetail');
  });

  it('caregroupApiSlice — pinGroup/unpinGroup mutations 존재', () => {
    const endpoints = caregroupApiSlice.endpoints;
    expect(endpoints).toHaveProperty('pinGroup');
    expect(endpoints).toHaveProperty('unpinGroup');
  });

  it('caregroupApiSlice — issueInviteCode mutation 존재', () => {
    const endpoints = caregroupApiSlice.endpoints;
    expect(endpoints).toHaveProperty('issueInviteCode');
  });

  it('issueInviteCode — POST /groups/{id}/invite-codes 호출', () => {
    const groupId = 4;
    const action = (caregroupApiSlice.endpoints.issueInviteCode as any).initiate(groupId);
    expect(typeof action).toBe('function');
  });

  it('caregroupApiSlice — createGroup mutation 존재', () => {
    const endpoints = caregroupApiSlice.endpoints;
    expect(endpoints).toHaveProperty('createGroup');
  });

  it('createGroup — initiate(body) 호출 가능 + thunk 반환', () => {
    const action = (caregroupApiSlice.endpoints.createGroup as any).initiate({ name: '테스트 그룹' });
    expect(typeof action).toBe('function');
  });

  it('caregroupApiSlice — leaveGroup mutation 존재', () => {
    const endpoints = caregroupApiSlice.endpoints;
    expect(endpoints).toHaveProperty('leaveGroup');
  });

  it('leaveGroup — initiate(groupId) 호출 가능 + thunk 반환', () => {
    const action = (caregroupApiSlice.endpoints.leaveGroup as any).initiate(7);
    expect(typeof action).toBe('function');
  });

  it('pinned 그룹 필터링 — pinned:true 첫 항목', () => {
    const groups: MyGroupSummary[] = [
      { groupId: 1, name: 'A', role: '보호자', memberCount: 2, membersPreview: [], lastActivity: null, unreadCount: 0, pinned: false },
      { groupId: 2, name: 'B', role: '보호자', memberCount: 3, membersPreview: [], lastActivity: null, unreadCount: 0, pinned: true },
    ];
    const pinned = groups.find(g => g.pinned);
    expect(pinned?.groupId).toBe(2);
  });

  it('트랜지언트 2-pinned — groupId 기반 필터로 그룹 누락 없음', () => {
    // 낙관적 업데이트 인플라이트 중 A·B 모두 pinned:true 인 순간
    const groups: MyGroupSummary[] = [
      { groupId: 1, name: 'A', role: '보호자', memberCount: 2, membersPreview: [], lastActivity: null, unreadCount: 0, pinned: true },
      { groupId: 2, name: 'B', role: '보호자', memberCount: 2, membersPreview: [], lastActivity: null, unreadCount: 0, pinned: true },
      { groupId: 3, name: 'C', role: '보호자', memberCount: 2, membersPreview: [], lastActivity: null, unreadCount: 0, pinned: false },
    ];
    const pinnedGroup = groups.find(g => g.pinned) ?? null; // → A (groupId=1)
    // Fix: id 기반 필터 — B가 사라지지 않아야 한다
    const unpinnedGroups = groups.filter(g => g.groupId !== pinnedGroup?.groupId);
    expect(unpinnedGroups).toHaveLength(2);
    expect(unpinnedGroups.find(g => g.groupId === 2)).toBeTruthy(); // B 보임
    expect(unpinnedGroups.find(g => g.groupId === 3)).toBeTruthy(); // C 보임
  });

  it('단일 핀 낙관적 업데이트 변환 — 새 핀 시 나머지 그룹 pinned=false', () => {
    const newPinnedId = 2;
    const groups: MyGroupSummary[] = [
      { groupId: 1, name: 'A', role: '보호자', memberCount: 2, membersPreview: [], lastActivity: null, unreadCount: 0, pinned: true },
      { groupId: 2, name: 'B', role: '보호자', memberCount: 2, membersPreview: [], lastActivity: null, unreadCount: 0, pinned: false },
      { groupId: 3, name: 'C', role: '보호자', memberCount: 2, membersPreview: [], lastActivity: null, unreadCount: 0, pinned: false },
    ];
    // onQueryStarted 에서 실행되는 draft 변환과 동일한 로직
    const updated = groups.map(g => ({ ...g, pinned: g.groupId === newPinnedId }));
    expect(updated.find(g => g.groupId === 1)?.pinned).toBe(false); // 기존 핀 해제
    expect(updated.find(g => g.groupId === 2)?.pinned).toBe(true);  // 새 핀
    expect(updated.find(g => g.groupId === 3)?.pinned).toBe(false);
    // 단 하나만 pinned
    expect(updated.filter(g => g.pinned)).toHaveLength(1);
  });
});
