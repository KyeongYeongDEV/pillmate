import type { MyGroupSummary } from '@/types/caregroup';
import { colors } from '@/styles/tokens';

export interface EventKindStyle {
  bg: string;
  fg: string;
  dot: string;
}

const EVENT_KIND: Record<string, EventKindStyle> = {
  done: { bg: colors.green95, fg: colors.green40, dot: colors.statusPositive },
  miss: { bg: colors.red95, fg: colors.red40, dot: colors.statusNegative },
  ai:   { bg: colors.violet95, fg: colors.violet45, dot: colors.violet45 },
  note: { bg: colors.yellow95, fg: colors.yellow40, dot: colors.cyan50 },
};

const DEFAULT_KIND: EventKindStyle = EVENT_KIND.note;

const ACTIVITY_TO_KIND: Record<string, keyof typeof EVENT_KIND> = {
  DOSE_TAKEN: 'done',
  DOSE_MISSED: 'miss',
  AI_INSIGHT: 'ai',
  AI_REPORT: 'ai',
  MEMBER_JOINED: 'note',
  PRESCRIPTION_ADDED: 'note',
  COMMENT: 'note',
};

export function resolveEventStyle(activityType: string | undefined): EventKindStyle {
  if (!activityType) return DEFAULT_KIND;
  const kind = ACTIVITY_TO_KIND[activityType];
  return kind ? EVENT_KIND[kind] : DEFAULT_KIND;
}

const PRIVATE_MEMBER_COUNT = 1;

export function isPersonalGroup(group: MyGroupSummary): boolean {
  return group.memberCount === PRIVATE_MEMBER_COUNT;
}

export function composeGroupDesc(group: MyGroupSummary): string {
  if (isPersonalGroup(group)) return '본인만 · 비공개';
  const preview = group.membersPreview.slice(0, 3).join('·');
  return preview ? `${group.memberCount}명 · ${preview}` : `${group.memberCount}명`;
}
