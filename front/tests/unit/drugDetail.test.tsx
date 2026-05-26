import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react-native';
import DrugHero from '@/components/drug/DrugHero';
import QuickStats from '@/components/drug/QuickStats';
import InteractionWarningCard from '@/components/drug/InteractionWarningCard';
import DetailTabs from '@/components/drug/DetailTabs';
import SearchResultCard from '@/components/search/SearchResultCard';
import type { DrugInteraction, DrugSearchResult } from '@/types/prescription';

jest.mock('@expo/vector-icons', () => ({
  Feather: ({ name }: any) => null,
}));

const DRUG: DrugSearchResult = {
  id: 1,
  kdCode: '670500700',
  name: '암로디핀정 5mg',
  company: '한미약품',
  imageUrl: null,
};

// ── DrugHero ──────────────────────────────────────────────────────

describe('DrugHero', () => {
  it('약 이름 렌더', () => {
    render(<DrugHero name="암로디핀정 5mg" englishName={null} category={null} company={null} />);
    expect(screen.getByText('암로디핀정 5mg')).toBeTruthy();
  });

  it('카테고리 렌더', () => {
    render(<DrugHero name="암로디핀정 5mg" englishName={null} category="혈압강하제 · CCB" company="한미약품" />);
    expect(screen.getByText('혈압강하제 · CCB')).toBeTruthy();
  });

  it('영문명 + 제조사 sub 렌더', () => {
    render(<DrugHero name="암로디핀정 5mg" englishName="Amlodipine Besylate" category={null} company="한미약품" />);
    expect(screen.getByText('Amlodipine Besylate · 한미약품')).toBeTruthy();
  });

  it('카테고리/영문명 null → 미노출', () => {
    const { toJSON } = render(<DrugHero name="암로디핀정 5mg" englishName={null} category={null} company={null} />);
    expect(screen.queryByText('혈압강하제')).toBeNull();
  });
});

// ── QuickStats ────────────────────────────────────────────────────

describe('QuickStats', () => {
  it('3개 카드 라벨 모두 렌더', () => {
    render(<QuickStats />);
    expect(screen.getByText('일일 복용')).toBeTruthy();
    expect(screen.getByText('복용 시각')).toBeTruthy();
    expect(screen.getByText('남은 일수')).toBeTruthy();
  });

  it('값 없으면 N/A 렌더', () => {
    render(<QuickStats />);
    expect(screen.getAllByText('N/A')).toHaveLength(3);
  });

  it('값 있으면 표시', () => {
    render(<QuickStats dailyDose="1정" timeOfDay="아침" remainingDays={23} />);
    expect(screen.getByText('1정')).toBeTruthy();
    expect(screen.getByText('아침')).toBeTruthy();
    expect(screen.getByText('23일')).toBeTruthy();
  });
});

// ── InteractionWarningCard ────────────────────────────────────────

describe('InteractionWarningCard', () => {
  const ddi: DrugInteraction[] = [
    { kdCode: 'DDI001', name: '이트라코나졸', category: '항진균제', description: '혈중 농도 상승 위험' },
  ];

  it('interactions 빈 배열 → null 렌더 (미노출)', () => {
    const { toJSON } = render(<InteractionWarningCard interactions={[]} />);
    expect(toJSON()).toBeNull();
  });

  it('DDI 1건 → 카드 + 약 이름 표시', () => {
    render(<InteractionWarningCard interactions={ddi} />);
    expect(screen.getByText('병용금기 1건')).toBeTruthy();
    expect(screen.getByText(/이트라코나졸/)).toBeTruthy();
  });

  it('description 표시', () => {
    render(<InteractionWarningCard interactions={ddi} />);
    expect(screen.getByText('혈중 농도 상승 위험')).toBeTruthy();
  });

  it('DDI 2건 → +1건 더 보기 표시', () => {
    const two = [...ddi, { kdCode: 'DDI002', name: '와파린', category: '항응고제', description: '출혈 위험' }];
    render(<InteractionWarningCard interactions={two} />);
    expect(screen.getByText('+1건 더 보기')).toBeTruthy();
  });
});

// ── DetailTabs ────────────────────────────────────────────────────

describe('DetailTabs', () => {
  const props = {
    efficacy: ['본태성 고혈압', '협심증'],
    dosage: ['의사 처방에 따라 복용'],
    warnings: ['임산부 주의'],
  };

  it('3개 탭 라벨 렌더', () => {
    render(<DetailTabs {...props} />);
    expect(screen.getByText('효능·효과')).toBeTruthy();
    expect(screen.getByText('용법·용량')).toBeTruthy();
    expect(screen.getByText('주의사항')).toBeTruthy();
  });

  it('기본 탭은 효능·효과 — 내용 보임', () => {
    render(<DetailTabs {...props} />);
    expect(screen.getByText('본태성 고혈압')).toBeTruthy();
  });

  it('용법·용량 탭 클릭 → 해당 내용 표시', () => {
    render(<DetailTabs {...props} />);
    fireEvent.press(screen.getByText('용법·용량'));
    expect(screen.getByText('의사 처방에 따라 복용')).toBeTruthy();
  });

  it('주의사항 탭 클릭 → 해당 내용 표시', () => {
    render(<DetailTabs {...props} />);
    fireEvent.press(screen.getByText('주의사항'));
    expect(screen.getByText('임산부 주의')).toBeTruthy();
  });
});

// ── SearchResultCard (갱신) — 행 tap = 상세, + = 추가 ────────────

describe('SearchResultCard — 2 액션', () => {
  it('상세 보기 버튼(카드 행) 렌더', () => {
    render(
      <SearchResultCard item={DRUG} query="" alreadyAdded={false}
        onAdd={jest.fn()} onDetail={jest.fn()} />,
    );
    expect(screen.getByTestId(`detail-btn-${DRUG.kdCode}`)).toBeTruthy();
  });

  it('추가 버튼 렌더', () => {
    render(
      <SearchResultCard item={DRUG} query="" alreadyAdded={false}
        onAdd={jest.fn()} onDetail={jest.fn()} />,
    );
    expect(screen.getByTestId(`add-btn-${DRUG.kdCode}`)).toBeTruthy();
  });

  it('카드 행 tap → onDetail 호출', () => {
    const onDetail = jest.fn();
    render(
      <SearchResultCard item={DRUG} query="" alreadyAdded={false}
        onAdd={jest.fn()} onDetail={onDetail} />,
    );
    fireEvent.press(screen.getByTestId(`detail-btn-${DRUG.kdCode}`));
    expect(onDetail).toHaveBeenCalledWith(DRUG);
  });

  it('+ 버튼 → onAdd 호출', () => {
    const onAdd = jest.fn();
    render(
      <SearchResultCard item={DRUG} query="" alreadyAdded={false}
        onAdd={onAdd} onDetail={jest.fn()} />,
    );
    fireEvent.press(screen.getByTestId(`add-btn-${DRUG.kdCode}`));
    expect(onAdd).toHaveBeenCalledWith(DRUG);
  });
});
