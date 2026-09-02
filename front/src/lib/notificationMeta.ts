import type { Feather } from '@expo/vector-icons';
import type { NotificationItem, NotificationType } from '@/types/notification';
import { colors } from '@/styles/tokens';

type FeatherName = keyof typeof Feather.glyphMap;

interface NotificationMeta {
  icon: FeatherName;
  color: string;
}

const META: Record<NotificationType, NotificationMeta> = {
  DOSE_TAKEN: { icon: 'check-circle', color: colors.statusPositive },
  DOSE_MISSED: { icon: 'alert-circle', color: colors.statusNegative },
  DOSE_CANCELED: { icon: 'rotate-ccw', color: colors.labelAlternative },
  DOSE_OVERDUE: { icon: 'clock', color: colors.statusCautionary },
  DOSE_NUDGE: { icon: 'bell', color: colors.primaryBase },
  DDI_CRITICAL: { icon: 'alert-triangle', color: colors.statusNegative },
  PRESCRIPTION_NEW: { icon: 'file-text', color: colors.primaryBase },
  WEEKLY_REPORT: { icon: 'bar-chart-2', color: colors.statusCautionary },
};

const DEFAULT_META: NotificationMeta = { icon: 'bell', color: colors.labelAlternative };

export function notificationMeta(type: NotificationType): NotificationMeta {
  return META[type] ?? DEFAULT_META;
}

export function notificationRoute(item: NotificationItem): string | null {
  if (item.doseLogId != null) return '/(tabs)/schedule';
  if (item.type === 'PRESCRIPTION_NEW') return '/(tabs)/prescriptions';
  return null;
}

export function unreadCount(items: NotificationItem[]): number {
  return items.filter(n => n.status !== 'READ').length;
}
