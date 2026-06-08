import React from 'react';
import { render, fireEvent } from '@testing-library/react-native';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { drugApiSlice } from '../../src/store/slices/drugApi';
import DrugSearchModal from '../../src/components/prescription/DrugSearchModal';

function makeStore() {
  return configureStore({
    reducer: { [drugApiSlice.reducerPath]: drugApiSlice.reducer },
    middleware: (gDM) => gDM().concat(drugApiSlice.middleware),
  });
}

function renderWithStore(ui: React.ReactElement) {
  const store = makeStore();
  return render(<Provider store={store}>{ui}</Provider>);
}

describe('DrugSearchModal', () => {
  it('visible=false 이면 렌더 안 함', () => {
    const { queryByText } = renderWithStore(
      <DrugSearchModal visible={false} onClose={jest.fn()} onSelect={jest.fn()} />,
    );
    expect(queryByText('약 검색')).toBeNull();
  });

  it('visible=true 이면 제목 렌더', () => {
    const { getByText } = renderWithStore(
      <DrugSearchModal visible={true} onClose={jest.fn()} onSelect={jest.fn()} />,
    );
    expect(getByText('약 검색')).toBeTruthy();
  });

  it('custom title 표시', () => {
    const { getByText } = renderWithStore(
      <DrugSearchModal visible={true} title="약 변경" onClose={jest.fn()} onSelect={jest.fn()} />,
    );
    expect(getByText('약 변경')).toBeTruthy();
  });

  it('✕ 버튼 누르면 onClose 호출', () => {
    const onClose = jest.fn();
    const { getAllByLabelText } = renderWithStore(
      <DrugSearchModal visible={true} onClose={onClose} onSelect={jest.fn()} />,
    );
    // backdrop + 버튼 중 role=button 인 닫기 버튼 선택
    const closeButtons = getAllByLabelText('닫기');
    fireEvent.press(closeButtons[closeButtons.length - 1]);
    expect(onClose).toHaveBeenCalled();
  });

  it('식약처 출처 안내 문구 표시 (medical-safety)', () => {
    const { getByText } = renderWithStore(
      <DrugSearchModal visible={true} onClose={jest.fn()} onSelect={jest.fn()} />,
    );
    expect(getByText(/식품의약품안전처/)).toBeTruthy();
  });
});
