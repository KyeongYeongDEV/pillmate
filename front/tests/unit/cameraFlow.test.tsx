import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react-native';
import CameraScreen from '@/app/prescription/camera';
import { prescriptionApi } from '@/lib/api/prescription';
import { router } from 'expo-router';

// ─── Mocks ────────────────────────────────────────────────────────────────────

jest.mock('expo-router', () => ({
  router: { replace: jest.fn(), push: jest.fn(), back: jest.fn() },
}));

jest.mock('expo-camera', () => ({
  CameraView: () => null,
  useCameraPermissions: () => [{ granted: true }, jest.fn()],
}));

jest.mock('expo-image-picker', () => ({
  requestMediaLibraryPermissionsAsync: jest.fn().mockResolvedValue({ status: 'granted' }),
  launchImageLibraryAsync: jest.fn().mockResolvedValue({
    canceled: false,
    assets: [{ uri: 'file://gallery.jpg' }],
  }),
}));

jest.mock('expo-haptics', () => ({
  notificationAsync: jest.fn().mockResolvedValue(undefined),
  impactAsync: jest.fn().mockResolvedValue(undefined),
  NotificationFeedbackType: { Success: 'SUCCESS' },
  ImpactFeedbackStyle: { Medium: 'MEDIUM' },
}));

jest.mock('react-native-safe-area-context', () => ({
  useSafeAreaInsets: () => ({ top: 0, bottom: 0, left: 0, right: 0 }),
}));

jest.mock('@/store/hooks', () => ({
  useAppDispatch: () => jest.fn(),
}));

jest.mock('@/store/slices/prescriptionFlowSlice', () => ({
  addFromExtract: jest.fn(() => ({ type: 'prescriptionFlow/addFromExtract' })),
  addFromOcr: jest.fn(() => ({ type: 'prescriptionFlow/addFromOcr' })),
  setImageKey: jest.fn(() => ({ type: 'prescriptionFlow/setImageKey' })),
}));

jest.mock('@/lib/api/prescription', () => ({
  prescriptionApi: {
    issueUploadUrl: jest.fn().mockResolvedValue({ uploadUrl: 'https://s3/put', objectKey: 'key-1' }),
    uploadToS3: jest.fn().mockResolvedValue(undefined),
    ocrExtract: jest.fn().mockResolvedValue({ items: [] }),
    ocr: jest.fn(),
  },
}));

jest.mock('@/hooks/useOcrInFlight', () => ({
  useOcrInFlight: () => ({
    begin: () => ({ allowed: true, elapsedMs: 0, attempts: 1 }),
    end: jest.fn(),
    hashImageUri: jest.fn().mockResolvedValue('hash-abc'),
  }),
}));

jest.mock('@/lib/imageProcessing', () => ({
  downsizeForOcr: jest.fn().mockResolvedValue({ uri: 'file://small.jpg', width: 1024, height: 1365 }),
}));

jest.mock('@/hooks/useCameraGuide', () => ({
  useCameraGuide: () => ({
    hints: { stability: 'ok', brightness: 'ok', tilt: 'ok' },
    allOk: false,
    reset: jest.fn(),
    warnShake: jest.fn(),
  }),
}));

jest.mock('@/components/prescription/CameraGuideOverlay', () => () => null);

// ─── Tests ────────────────────────────────────────────────────────────────────

describe('camera 등록 흐름 (B방식 통일)', () => {
  beforeEach(() => jest.clearAllMocks());

  it('갤러리 선택 → ocrExtract 호출(자동등록 ocr 미사용)', async () => {
    render(<CameraScreen />);
    fireEvent.press(screen.getByLabelText('갤러리'));

    await waitFor(() => {
      expect(prescriptionApi.ocrExtract).toHaveBeenCalledTimes(1);
    });
    expect(prescriptionApi.ocr).not.toHaveBeenCalled();
  });

  it('인식 후 review.tsx 로 라우팅 (confirm 미경유)', async () => {
    render(<CameraScreen />);
    fireEvent.press(screen.getByLabelText('갤러리'));

    await waitFor(() => {
      expect(router.replace).toHaveBeenCalledWith('/prescription/review');
    });
    expect(router.replace).not.toHaveBeenCalledWith('/prescription/confirm');
  });
});
