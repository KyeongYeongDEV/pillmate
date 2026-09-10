export interface LastActivitySummary {
  summary: string;
  activityType: string;
  severity: string;
  occurredAt: string;
}

export interface MemberPreview {
  userId: number;
  name: string;
  color: string | null;
}

export interface MyGroupSummary {
  groupId: number;
  name: string;
  role: string;
  memberCount: number;
  // 목록에서도 상세와 같은 구성원 고유색을 쓰려면 userId 가 필요하다(색 배정 기준이 userId).
  // 서버가 userId 오름차순 상위 N 명을 보내므로 이 배열의 순위 = 전체 구성원 기준 순위다.
  membersPreview: MemberPreview[];
  lastActivity: LastActivitySummary | null;
  unreadCount: number;
  pinned: boolean;
}

export interface MemberView {
  userId: number;
  name: string;
  role: string;
  color: string | null;
}

export interface InviteCodeView {
  code: string;
  expiresAt: string;
}

export interface ShareSettingView {
  userId: number;
  name: string;
  role: string;
  shared: boolean;
}

export interface ActivityView {
  actorName: string;
  activityType: string;
  summary: string;
  occurredAt: string;
}

export interface GroupDetailResponse {
  groupId: number;
  name: string;
  memberCount: number;
  members: MemberView[];
  inviteCode: InviteCodeView;
  recentActivities: ActivityView[];
}
