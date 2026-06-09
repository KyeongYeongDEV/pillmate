import React from 'react';
import { render } from '@testing-library/react-native';
import CameraGuideOverlay from '../../src/components/prescription/CameraGuideOverlay';
import type { CameraHints } from '../../src/hooks/useCameraGuide';

const allOkHints: CameraHints = { stability: 'ok', brightness: 'ok', tilt: 'ok' };
const loadingHints: CameraHints = { stability: 'loading', brightness: 'ok', tilt: 'ok' };
const warnHints: CameraHints = { stability: 'warn', brightness: 'ok', tilt: 'ok' };

describe('CameraGuideOverlay', () => {
  it('hint 3개 모두 렌더링', () => {
    const { getByLabelText } = render(
      <CameraGuideOverlay hints={allOkHints} allOk={true} />,
    );
    expect(getByLabelText('흔들림 ok')).toBeTruthy();
    expect(getByLabelText('조명 ok')).toBeTruthy();
    expect(getByLabelText('각도 ok')).toBeTruthy();
  });

  it('stability=loading → 흔들림 loading 레이블', () => {
    const { getByLabelText } = render(
      <CameraGuideOverlay hints={loadingHints} allOk={false} />,
    );
    expect(getByLabelText('흔들림 loading')).toBeTruthy();
  });

  it('stability=warn → 흔들림 warn 레이블', () => {
    const { getByLabelText } = render(
      <CameraGuideOverlay hints={warnHints} allOk={false} />,
    );
    expect(getByLabelText('흔들림 warn')).toBeTruthy();
  });

  it('allOk=false 일 때 기본 안내 문구 표시', () => {
    const { getByText } = render(
      <CameraGuideOverlay hints={loadingHints} allOk={false} />,
    );
    expect(getByText('여기에 처방전을 맞춰주세요')).toBeTruthy();
  });

  it('allOk=true + autoShutterCountdown=null 시 안내 문구 표시', () => {
    const { getByText } = render(
      <CameraGuideOverlay hints={allOkHints} allOk={true} autoShutterCountdown={null} />,
    );
    expect(getByText('처방전을 맞춰주세요')).toBeTruthy();
  });

  it('autoShutterCountdown=3 시 카운트다운 텍스트 표시', () => {
    const { getByText } = render(
      <CameraGuideOverlay hints={allOkHints} allOk={true} autoShutterCountdown={3} />,
    );
    expect(getByText('3초 후 자동 촬영')).toBeTruthy();
  });

  it('커스텀 frameWidth/frameHeight 적용', () => {
    const { toJSON } = render(
      <CameraGuideOverlay hints={allOkHints} allOk={true} frameWidth={320} frameHeight={420} />,
    );
    expect(toJSON()).toBeTruthy();
  });
});
