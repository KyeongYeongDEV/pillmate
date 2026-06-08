import React from 'react';
import { render, fireEvent } from '@testing-library/react-native';
import DrugMatchCard from '../../src/components/prescription/DrugMatchCard';
import type { DrugListItem } from '../../src/types/prescription';

const baseItem: DrugListItem = {
  id: 'test-1',
  source: 'OCR_AUTO',
  kdCode: '670500700',
  nameRaw: '암로디핀정',
  matchedName: '암로디핀정 5mg',
  imageUrl: null,
  confidence: 0.95,
  doseAmount: 1,
  doseUnit: '정',
  frequency: 1,
  durationDays: 7,
  slots: { morning: true, noon: false, evening: false, bedtime: false },
  decision: 'AUTO',
};

describe('DrugMatchCard — confidence 색상 분기', () => {
  it('높음(≥0.8): 녹색 배지 표시', () => {
    const { getByText } = render(
      <DrugMatchCard item={baseItem} onReplace={jest.fn()} onRemove={jest.fn()} />,
    );
    expect(getByText(/높음 95%/)).toBeTruthy();
  });

  it('보통(0.5~0.8): 주황 배지 표시', () => {
    const item: DrugListItem = { ...baseItem, id: 'test-2', confidence: 0.65 };
    const { getByText } = render(
      <DrugMatchCard item={item} onReplace={jest.fn()} onRemove={jest.fn()} />,
    );
    expect(getByText(/보통 65%/)).toBeTruthy();
  });

  it('낮음(<0.5): 빨간 배지 + 경고 배너 표시', () => {
    const item: DrugListItem = { ...baseItem, id: 'test-3', confidence: 0.3 };
    const { getByText } = render(
      <DrugMatchCard item={item} onReplace={jest.fn()} onRemove={jest.fn()} />,
    );
    expect(getByText(/낮음 30%/)).toBeTruthy();
    expect(getByText(/매칭 신뢰도가 낮아요/)).toBeTruthy();
  });

  it('인식 실패(kdCode=null): 인식 실패 배지 + 경고 배너', () => {
    const item: DrugListItem = { ...baseItem, id: 'test-4', kdCode: null, matchedName: null, confidence: null };
    const { getByText } = render(
      <DrugMatchCard item={item} onReplace={jest.fn()} onRemove={jest.fn()} />,
    );
    expect(getByText(/인식 실패/)).toBeTruthy();
    expect(getByText(/식약처 DB에서 자동 확인되지 않았어요/)).toBeTruthy();
  });
});

describe('DrugMatchCard — 액션 콜백', () => {
  it('수정 버튼 누르면 onReplace(id) 호출', () => {
    const onReplace = jest.fn();
    const { getByLabelText } = render(
      <DrugMatchCard item={baseItem} onReplace={onReplace} onRemove={jest.fn()} />,
    );
    fireEvent.press(getByLabelText('다른 약으로 수정'));
    expect(onReplace).toHaveBeenCalledWith('test-1');
  });

  it('삭제 버튼 누르면 onRemove(id) 호출', () => {
    const onRemove = jest.fn();
    const { getByLabelText } = render(
      <DrugMatchCard item={baseItem} onReplace={jest.fn()} onRemove={onRemove} />,
    );
    fireEvent.press(getByLabelText('약 삭제'));
    expect(onRemove).toHaveBeenCalledWith('test-1');
  });

  it('medical-safety: 식약처 출처 표시', () => {
    const { getByText } = render(
      <DrugMatchCard item={baseItem} onReplace={jest.fn()} onRemove={jest.fn()} />,
    );
    expect(getByText(/식품의약품안전처/)).toBeTruthy();
  });
});
