import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react-native';
import InsightCard, { stripInsightDisclaimer } from '@/components/home/InsightCard';

describe('stripInsightDisclaimer', () => {
  it('말미 면책 문구 제거', () => {
    expect(stripInsightDisclaimer('비타민 D 보충이 도움될 수 있어요. 참고용입니다. 약사·의사와 상담하세요.'))
      .toBe('비타민 D 보충이 도움될 수 있어요.');
  });

  it('마침표/가운뎃점 변형도 제거', () => {
    expect(stripInsightDisclaimer('내용. 참고용입니다. 약사.의사와 상담하세요'))
      .toBe('내용.');
  });

  it('면책 문구 없으면 원문 유지', () => {
    expect(stripInsightDisclaimer('내용만 있어요.')).toBe('내용만 있어요.');
  });
});

describe('InsightCard', () => {
  it('메시지와 상세 내용 표시', () => {
    render(
      <InsightCard
        severity="WARN"
        message="저녁약을 자주 빠뜨려요"
        detail="지난 30일 중 7일 누락"
      />,
    );
    expect(screen.getByText('저녁약을 자주 빠뜨려요')).toBeTruthy();
    expect(screen.getByText(/지난 30일/)).toBeTruthy();
  });

  it('닫기 버튼이 있을 때 onClose 호출', () => {
    const onClose = jest.fn();
    render(
      <InsightCard severity="INFO" message="msg" detail="detail" onClose={onClose} />,
    );
    fireEvent.press(screen.getByLabelText('AI 분석 카드 닫기'));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('CRITICAL severity 에도 렌더', () => {
    render(
      <InsightCard severity="CRITICAL" message="위험" detail="심각한 문제" />,
    );
    expect(screen.getByText('위험')).toBeTruthy();
  });

  it('자세히 보기 버튼 onDetail 호출', () => {
    const onDetail = jest.fn();
    render(
      <InsightCard severity="WARN" message="msg" detail="d" onDetail={onDetail} />,
    );
    fireEvent.press(screen.getByLabelText('자세히 보기'));
    expect(onDetail).toHaveBeenCalledTimes(1);
  });

  it('subtitle 전달 시 렌더', () => {
    render(
      <InsightCard
        severity="INFO"
        message="msg"
        detail="d"
        subtitle="9월 12일 등록 약봉투 (약 3개) 기준"
      />,
    );
    expect(screen.getByText('9월 12일 등록 약봉투 (약 3개) 기준')).toBeTruthy();
  });

  it('subtitle 없으면 렌더 안 함', () => {
    render(<InsightCard severity="INFO" message="msg" detail="d" />);
    expect(screen.queryByText(/기준/)).toBeNull();
  });
});
