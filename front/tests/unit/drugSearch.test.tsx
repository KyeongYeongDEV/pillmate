import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react-native';
import Highlight from '@/components/search/Highlight';
import DrugSearchBar from '@/components/search/DrugSearchBar';
import SearchResultCard from '@/components/search/SearchResultCard';
import CategoryGrid from '@/components/search/CategoryGrid';
import RecentSearchChips from '@/components/search/RecentSearchChips';
import type { DrugSearchResult } from '@/types/prescription';

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

// ── Highlight ─────────────────────────────────────────────────────────

describe('Highlight', () => {
  it('term 없으면 원문 그대로 렌더', () => {
    render(<Highlight text="암로디핀정 5mg" term="" />);
    expect(screen.getByText('암로디핀정 5mg')).toBeTruthy();
  });

  it('term 일치 구간은 별도 Text 노드로 분리 (색상 강조)', () => {
    render(<Highlight text="암로디핀정 5mg" term="암로" />);
    // 강조 구간은 자체 Text 노드
    expect(screen.getByText('암로')).toBeTruthy();
    // 나머지 텍스트는 부모 Text 안에 포함됨
    expect(screen.getByText(/암로디핀정 5mg/)).toBeTruthy();
  });

  it('대소문자 무관하게 매칭', () => {
    render(<Highlight text="Amlodipine 5mg" term="aml" />);
    expect(screen.getByText('Aml')).toBeTruthy();
  });

  it('term 미매칭이면 원문 전체 렌더', () => {
    render(<Highlight text="암로디핀정 5mg" term="메트포르민" />);
    expect(screen.getByText('암로디핀정 5mg')).toBeTruthy();
  });
});

// ── DrugSearchBar ─────────────────────────────────────────────────────

describe('DrugSearchBar', () => {
  it('빈 값이면 clear 버튼 미노출', () => {
    render(<DrugSearchBar value="" onChangeText={jest.fn()} onClear={jest.fn()} />);
    expect(screen.queryByRole('button', { name: '검색어 지우기' })).toBeNull();
  });

  it('값 있으면 clear 버튼 노출', () => {
    render(<DrugSearchBar value="암로" onChangeText={jest.fn()} onClear={jest.fn()} />);
    expect(screen.getByRole('button', { name: '검색어 지우기' })).toBeTruthy();
  });

  it('clear 버튼 클릭 시 onClear 호출', () => {
    const onClear = jest.fn();
    render(<DrugSearchBar value="암로" onChangeText={jest.fn()} onClear={onClear} />);
    fireEvent.press(screen.getByRole('button', { name: '검색어 지우기' }));
    expect(onClear).toHaveBeenCalledTimes(1);
  });
});

// ── SearchResultCard ──────────────────────────────────────────────────

describe('SearchResultCard', () => {
  it('약 이름 렌더', () => {
    render(<SearchResultCard item={DRUG} query="" alreadyAdded={false} onAdd={jest.fn()} onDetail={jest.fn()} />);
    expect(screen.getByText('암로디핀정 5mg')).toBeTruthy();
  });

  it('제약사 렌더', () => {
    render(<SearchResultCard item={DRUG} query="" alreadyAdded={false} onAdd={jest.fn()} onDetail={jest.fn()} />);
    expect(screen.getByText('한미약품')).toBeTruthy();
  });

  it('alreadyAdded=true → 추가됨 뱃지 표시', () => {
    render(<SearchResultCard item={DRUG} query="" alreadyAdded onAdd={jest.fn()} onDetail={jest.fn()} />);
    expect(screen.getByText('추가됨')).toBeTruthy();
  });

  it('onAdd 콜백 호출', () => {
    const onAdd = jest.fn();
    render(<SearchResultCard item={DRUG} query="" alreadyAdded={false} onAdd={onAdd} onDetail={jest.fn()} />);
    fireEvent.press(screen.getByTestId(`add-btn-${DRUG.kdCode}`));
    expect(onAdd).toHaveBeenCalledWith(DRUG);
  });
});

// ── CategoryGrid ──────────────────────────────────────────────────────

describe('CategoryGrid', () => {
  it('6개 카테고리 모두 렌더', () => {
    render(<CategoryGrid onSelect={jest.fn()} />);
    expect(screen.getByText('고혈압')).toBeTruthy();
    expect(screen.getByText('당뇨')).toBeTruthy();
    expect(screen.getByText('콜레스테롤')).toBeTruthy();
    expect(screen.getByText('소화제')).toBeTruthy();
    expect(screen.getByText('진통제')).toBeTruthy();
    expect(screen.getByText('감기')).toBeTruthy();
  });

  it('카테고리 클릭 시 onSelect 호출', () => {
    const onSelect = jest.fn();
    render(<CategoryGrid onSelect={onSelect} />);
    fireEvent.press(screen.getByRole('button', { name: '고혈압 카테고리 검색' }));
    expect(onSelect).toHaveBeenCalledWith('고혈압');
  });
});

// ── RecentSearchChips ─────────────────────────────────────────────────

describe('RecentSearchChips', () => {
  const items = ['메트포르민', '오메가-3'];

  it('chips 렌더', () => {
    render(<RecentSearchChips items={items} onSelect={jest.fn()} onRemove={jest.fn()} onClearAll={jest.fn()} />);
    expect(screen.getByText('메트포르민')).toBeTruthy();
    expect(screen.getByText('오메가-3')).toBeTruthy();
  });

  it('empty items 이면 null 렌더 (섹션 미노출)', () => {
    const { toJSON } = render(
      <RecentSearchChips items={[]} onSelect={jest.fn()} onRemove={jest.fn()} onClearAll={jest.fn()} />,
    );
    expect(toJSON()).toBeNull();
  });

  it('onClearAll 버튼 클릭', () => {
    const onClearAll = jest.fn();
    render(<RecentSearchChips items={items} onSelect={jest.fn()} onRemove={jest.fn()} onClearAll={onClearAll} />);
    fireEvent.press(screen.getByRole('button', { name: '최근 검색 전체 삭제' }));
    expect(onClearAll).toHaveBeenCalledTimes(1);
  });
});
