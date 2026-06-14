import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react-native';
import DrugHero from '@/components/drug/DrugHero';
import DrugInfoSection from '@/components/drug/DrugInfoSection';
import SourceCard from '@/components/drug/SourceCard';
import SearchResultCard from '@/components/search/SearchResultCard';
import type { DrugSearchResult } from '@/types/prescription';

jest.mock('@expo/vector-icons', () => ({
  Feather: ({ name }: any) => null,
}));

const DRUG: DrugSearchResult = {
  id: 1,
  kdCode: '670500700',
  name: '암로디핀정 5mg',
  ingredient: '암로디핀베실산염',
  efficacy: '본태성 고혈압',
  form: '정제',
  imageUrl: null,
};

// ── DrugHero ──────────────────────────────────────────────────────

describe('DrugHero', () => {
  it('약 이름 렌더', () => {
    render(<DrugHero name="암로디핀정 5mg" company={null} ingredient={null} form={null} imageUrl={null} />);
    expect(screen.getByText('암로디핀정 5mg')).toBeTruthy();
  });

  it('제형 · 제조사 메타 렌더', () => {
    render(<DrugHero name="암로디핀정 5mg" company="한미약품" ingredient={null} form="정제" imageUrl={null} />);
    expect(screen.getByText('정제 · 한미약품')).toBeTruthy();
  });

  it('성분 렌더', () => {
    render(<DrugHero name="암로디핀정 5mg" company={null} ingredient="암로디핀베실산염" form={null} imageUrl={null} />);
    expect(screen.getByText('암로디핀베실산염')).toBeTruthy();
  });

  it('모든 메타 null → 이름만, 크래시 X', () => {
    expect(() =>
      render(<DrugHero name="암로디핀정 5mg" company={null} ingredient={null} form={null} imageUrl={null} />),
    ).not.toThrow();
  });
});

// ── DrugInfoSection ───────────────────────────────────────────────

describe('DrugInfoSection', () => {
  it('제목과 본문 렌더', () => {
    render(<DrugInfoSection title="효능·효과" text="본태성 고혈압에 사용합니다." />);
    expect(screen.getByText('효능·효과')).toBeTruthy();
    expect(screen.getByText('본태성 고혈압에 사용합니다.')).toBeTruthy();
  });

  it('text null → "정보 없음" 렌더', () => {
    render(<DrugInfoSection title="부작용" text={null} />);
    expect(screen.getByText('정보 없음')).toBeTruthy();
  });

  it('text 공백만 → "정보 없음" 렌더', () => {
    render(<DrugInfoSection title="용법·용량" text="   " />);
    expect(screen.getByText('정보 없음')).toBeTruthy();
  });
});

// ── SourceCard ────────────────────────────────────────────────────

describe('SourceCard', () => {
  it('source 텍스트 렌더', () => {
    render(<SourceCard source="식품의약품안전처 의약품안전나라" />);
    expect(screen.getByText('출처: 식품의약품안전처 의약품안전나라')).toBeTruthy();
  });

  it('source null → 기본 출처 렌더', () => {
    render(<SourceCard source={null} />);
    expect(screen.getByText('출처: 식품의약품안전처 의약품안전나라')).toBeTruthy();
  });
});

// ── SearchResultCard — 행 tap = 상세, + = 추가 ────────────────────

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
