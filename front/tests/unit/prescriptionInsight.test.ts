import { buildInsightSubtitle } from '../../src/lib/prescriptionInsight';

describe('buildInsightSubtitle', () => {
  it('등록일 + 약 개수 라벨 빌드', () => {
    expect(buildInsightSubtitle('2026-09-12', 3)).toBe('9월 12일 등록 약봉투 (약 3개) 기준');
  });

  it('선행 0 제거 (1월 5일)', () => {
    expect(buildInsightSubtitle('2026-01-05', 1)).toBe('1월 5일 등록 약봉투 (약 1개) 기준');
  });

  it('ISO 타임스탬프도 날짜만 사용', () => {
    expect(buildInsightSubtitle('2026-12-25T09:30:00Z', 5)).toBe('12월 25일 등록 약봉투 (약 5개) 기준');
  });
});
