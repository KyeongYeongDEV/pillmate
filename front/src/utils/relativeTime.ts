const MINUTE_MS = 60000;
const HOUR_MIN = 60;
const DAY_HOUR = 24;

export function relativeTime(iso: string, now: number = Date.now()): string {
  const diffMin = Math.floor((now - new Date(iso).getTime()) / MINUTE_MS);
  if (diffMin < 1) return '방금';
  if (diffMin < HOUR_MIN) return `${diffMin}분 전`;
  const diffH = Math.floor(diffMin / HOUR_MIN);
  if (diffH < DAY_HOUR) return `${diffH}시간 전`;
  return `${Math.floor(diffH / DAY_HOUR)}일 전`;
}
