export function buildInsightSubtitle(prescribedAt: string, drugCount: number): string {
  const [, month, day] = prescribedAt.slice(0, 10).split('-');
  return `${Number(month)}월 ${Number(day)}일 등록 약봉투 (약 ${drugCount}개) 기준`;
}
