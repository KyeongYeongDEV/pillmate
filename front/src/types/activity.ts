export type ActivitySeverity = 'INFO' | 'WARN';

export type ActivityType = 'DOSE_TAKEN' | 'DOSE_MISSED';

export type TimeSlot = 'MORNING' | 'NOON' | 'EVENING' | 'BEDTIME';

export interface ActivityFeedItem {
  actorNickname: string;
  activityType: ActivityType;
  timeSlot: TimeSlot;
  summary: string;
  severity: ActivitySeverity;
  occurredAt: string; // ISO 8601
}
