import React from 'react';
import { render, screen } from '@testing-library/react-native';
import DDIWarningCard from '@/components/prescription/DDIWarningCard';
import type { InteractionWarning } from '@/types/prescription';

const CRITICAL: InteractionWarning = {
  drugCodeA: 'KD001', drugCodeB: 'KD002',
  nameA: '와파린정 2mg', nameB: '아스피린정 100mg',
  severity: 'CRITICAL',
  description: '와파린의 항응고 효과가 증가하여 출혈 위험이 높아집니다.',
  source: '식품의약품안전처',
};

const HIGH: InteractionWarning = {
  ...CRITICAL, severity: 'HIGH', drugCodeA: 'KD003', drugCodeB: 'KD004',
  nameA: '디곡신정', nameB: '아미오다론정',
};

const MEDIUM: InteractionWarning = {
  ...CRITICAL, severity: 'MEDIUM', drugCodeA: 'KD005', drugCodeB: 'KD006',
  nameA: '심바스타틴정', nameB: '시메티딘정',
};

const LOW: InteractionWarning = {
  ...CRITICAL, severity: 'LOW', drugCodeA: 'KD007', drugCodeB: 'KD008',
  nameA: '아세트아미노펜정', nameB: '카페인정',
};

describe('DDIWarningCard', () => {
  it('CRITICAL — 약 쌍 + description + source 모두 렌더', () => {
    render(<DDIWarningCard warning={CRITICAL} />);
    expect(screen.getByText(/와파린정 2mg/)).toBeTruthy();
    expect(screen.getByText(/아스피린정 100mg/)).toBeTruthy();
    expect(screen.getByText(/출혈 위험/)).toBeTruthy();
    expect(screen.getByText('출처: 식품의약품안전처')).toBeTruthy();
  });

  it('CRITICAL — severity chip 라벨 "위험" 렌더', () => {
    render(<DDIWarningCard warning={CRITICAL} />);
    expect(screen.getByText('위험')).toBeTruthy();
  });

  it('HIGH — severity chip 라벨 "주의" 렌더', () => {
    render(<DDIWarningCard warning={HIGH} />);
    expect(screen.getByText('주의')).toBeTruthy();
  });

  it('MEDIUM — severity chip 라벨 "보통" 렌더', () => {
    render(<DDIWarningCard warning={MEDIUM} />);
    expect(screen.getByText('보통')).toBeTruthy();
  });

  it('LOW — severity chip 라벨 "경미" 렌더', () => {
    render(<DDIWarningCard warning={LOW} />);
    expect(screen.getByText('경미')).toBeTruthy();
  });

  it('a11y — accessibilityLabel 에 약 쌍 + severity 포함', () => {
    render(<DDIWarningCard warning={CRITICAL} />);
    expect(screen.getByLabelText(/와파린정 2mg.*아스피린정 100mg.*CRITICAL/)).toBeTruthy();
  });
});
