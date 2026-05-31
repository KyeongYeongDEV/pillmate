export interface LastActivitySummary {
  summary: string;
  activityType: string;
  severity: string;
  occurredAt: string;
}

export interface MyGroupSummary {
  groupId: number;
  name: string;
  role: string;
  memberCount: number;
  membersPreview: string[];
  lastActivity: LastActivitySummary | null;
  unreadCount: number;
  pinned: boolean;
}

export interface MemberView {
  userId: number;
  name: string;
  role: string;
}

export interface InviteCodeView {
  code: string;
  expiresAt: string;
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
