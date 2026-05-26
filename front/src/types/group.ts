export type MemberRole = '환자' | '보호자';

export interface GroupMember {
  id: string;
  name: string;
  sub: string;
  role: MemberRole;
  tint: string;
  online: boolean;
  isMe?: boolean;
}

export type ActivityKind = 'done' | 'miss' | 'ai' | 'rx' | 'note';

export interface GroupActivity {
  id: string;
  who: string;
  whoLabel: string;
  tint: string;
  time: string;
  kind: ActivityKind;
  title: string;
  detail: string | string[];
  pills?: string[];
  cta?: string;
}
