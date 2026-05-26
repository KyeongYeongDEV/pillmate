import React from 'react';
import { render, screen } from '@testing-library/react-native';
import AiBubble from '@/components/chat/AiBubble';
import UserBubble from '@/components/chat/UserBubble';
import type { ChatMessage } from '@/types/chat';

const AI_MSG: ChatMessage = {
  id: 'ai-1',
  role: 'ai',
  content: '암로디핀은 일반 감기약과 함께 복용 가능합니다.',
  hasWarning: true,
  warningText: '일부 감기약은 주의가 필요해요',
  sources: [
    { organization: '식약처 의약품안전나라', document: '암로디핀정 병용주의' },
  ],
};

const USER_MSG: ChatMessage = {
  id: 'user-1',
  role: 'user',
  content: '혈압약 감기약 같이 먹어도 되나요?',
};

describe('AiBubble', () => {
  it('AI 메시지 콘텐츠 렌더', () => {
    render(<AiBubble message={AI_MSG} />);
    expect(screen.getByText(/암로디핀은/)).toBeTruthy();
  });

  it('경고 텍스트 렌더', () => {
    render(<AiBubble message={AI_MSG} />);
    expect(screen.getByText(/일부 감기약은 주의가/)).toBeTruthy();
  });

  it('출처 렌더', () => {
    render(<AiBubble message={AI_MSG} />);
    expect(screen.getByText('식약처 의약품안전나라')).toBeTruthy();
    expect(screen.getByText('암로디핀정 병용주의')).toBeTruthy();
  });

  it('의료 안전 — 약사·의사 상담 푸터 항상 표시', () => {
    render(<AiBubble message={AI_MSG} />);
    expect(screen.getByText(/약사·의사/)).toBeTruthy();
  });
});

describe('UserBubble', () => {
  it('유저 메시지 콘텐츠 렌더', () => {
    render(<UserBubble message={USER_MSG} />);
    expect(screen.getByText('혈압약 감기약 같이 먹어도 되나요?')).toBeTruthy();
  });
});
