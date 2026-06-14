export type NotificationType =
  | 'DOSE_TAKEN'
  | 'DOSE_MISSED'
  | 'DOSE_CANCELED'
  | 'DDI_CRITICAL'
  | 'PRESCRIPTION_NEW'
  | 'WEEKLY_REPORT';

export type NotificationStatus = 'PENDING' | 'SENT' | 'FAILED' | 'READ';

export interface NotificationItem {
  id: number;
  type: NotificationType;
  title: string;
  body: string;
  status: NotificationStatus;
  doseLogId: number | null;
  createdAt: string;
}
