import React from 'react';
import { render } from '@testing-library/react-native';
import CameraGuideOverlay from '../../src/components/prescription/CameraGuideOverlay';

describe('CameraGuideOverlay (미니멀 — 프레임 + 안내 1줄)', () => {
  it('props 없이 렌더 (네모칸 프레임)', () => {
    const { toJSON } = render(<CameraGuideOverlay />);
    expect(toJSON()).toBeTruthy();
  });

  it('촬영 유도 메인 문구 + 개인정보 주의 문구 노출', () => {
    const { getByText } = render(<CameraGuideOverlay />);
    expect(getByText('알약 정보만 있으면 돼요!')).toBeTruthy();
    expect(getByText('⚠️ 주민번호는 가려주세요')).toBeTruthy();
  });

  it('#52 제거 요소 미재도입 — 힌트/흔들림/이전 안내문구 미노출', () => {
    const { queryByText } = render(<CameraGuideOverlay />);
    expect(queryByText('여기에 약봉투를 맞춰주세요')).toBeNull();
    expect(queryByText('약봉투를 맞춰주세요')).toBeNull();
    expect(queryByText('흔들림')).toBeNull();
  });

  it('커스텀 frameWidth/frameHeight 적용', () => {
    const { toJSON } = render(<CameraGuideOverlay frameWidth={320} frameHeight={420} />);
    expect(toJSON()).toBeTruthy();
  });
});
