import React from 'react';
import { render } from '@testing-library/react-native';
import DrugCard from '../../src/components/prescription/DrugCard';
import type { DrugListItem } from '../../src/types/prescription';

const baseItem: DrugListItem = {
  id: 'test-1',
  source: 'OCR_AUTO',
  kdCode: 'KD001',
  nameRaw: '암로디핀정 5mg',
  matchedName: '암로디핀정 5mg',
  imageUrl: null,
  confidence: 0.98,
  doseAmount: 1,
  doseUnit: '정',
  frequency: 1,
  durationDays: 7,
  decision: 'AUTO',
};

describe('DrugCard', () => {
  it('renders matched drug name', () => {
    const { getByText } = render(
      <DrugCard item={baseItem} onRemove={jest.fn()} />
    );
    expect(getByText('암로디핀정 5mg')).toBeTruthy();
  });

  it('renders MFDS source for OCR_AUTO item', () => {
    const { getByText } = render(
      <DrugCard item={baseItem} onRemove={jest.fn()} />
    );
    expect(getByText(/식품의약품안전처/)).toBeTruthy();
  });

  it('renders user-input source for MANUAL_INPUT', () => {
    const manualItem: DrugListItem = {
      ...baseItem,
      id: 'manual-1',
      source: 'MANUAL_INPUT',
      kdCode: null,
      matchedName: null,
      confidence: null,
    };
    const { getByText } = render(
      <DrugCard item={manualItem} onRemove={jest.fn()} />
    );
    expect(getByText(/사용자 입력/)).toBeTruthy();
  });

  it('shows warning badge for low confidence', () => {
    const lowConfItem: DrugListItem = { ...baseItem, id: 'low-1', confidence: 0.5 };
    const { getByLabelText } = render(
      <DrugCard item={lowConfItem} onRemove={jest.fn()} />
    );
    expect(getByLabelText('신뢰도 낮음')).toBeTruthy();
  });

  it('shows unmatched warning for null kdCode OCR item', () => {
    const unmatchedItem: DrugListItem = {
      ...baseItem,
      id: 'unmatched-1',
      kdCode: null,
      confidence: 0.45,
      source: 'OCR_AUTO',
    };
    const { getByText } = render(
      <DrugCard item={unmatchedItem} onRemove={jest.fn()} />
    );
    expect(getByText(/자동 확인되지 않았어요/)).toBeTruthy();
  });
});
