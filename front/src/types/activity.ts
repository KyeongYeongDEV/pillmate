export type ActivitySeverity = 'INFO' | 'WARN' | 'CRITICAL';

export type ActivityType =
  | 'DOSE_TAKEN'
  | 'DOSE_MISSED'
  | 'PRESCRIPTION_REGISTERED'
  | 'PRESCRIPTION_UPDATED'
  | 'SYSTEM';

export interface ActivityFeedItem {
  id: number;
  actorUserId: number;
  actorName: string;
  activityType: ActivityType;
  summary: string;
  severity: ActivitySeverity;
  occurredAt: string; // ISO 8601
}
